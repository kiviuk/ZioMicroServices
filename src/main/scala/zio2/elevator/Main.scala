package zio2.elevator

import zio2.elevator.Request.makeQueue
import zio2.elevator
import zio2.elevator.ElevatorLog.{fileLogElevatorStats, logLine, logHeader}

import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault}
import zio.stm.{STM, TPriorityQueue}
import sun.security.provider.NativePRNG.Blocking
import zio.nio.charset.Charset
import zio.nio.file.{Files, Path}
import zio.stream.{ZSink, ZStream}

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import scala.collection.mutable
import scala.io.Source
import zio.stm.ZSTM
import zio.Ref
//import zio2.elevator.Experiment.WorkerLayer

// TODO: Problem: The elevator is constantly moving and needs to decide between multiple requests.
//       To solve this, a nearest next floor algorithm can be used.
//       However, a priority queue is needed that can re-prioritize depending on the elevator's current floor.
//       How about a self-reorganizing data structure for elevators? When the elevator is already heading up,
//       the next nearest floor destination becomes the head of the data structure, always adjusted for the
//       current elevator floor position. Every time the elevator reaches a floor,
//       that floor is removed from the data structure.
//       research: https://www.youtube.com/watch?v=6JxvKfSV9Ns
//       JGraphTs,  org.jgrapht.util

// idea to implement 1 global queue with multiple views operating on that queue.
// view 1 select inside requests by elevator-id
// view 2 selects upwards requests
// view 3 selects downwards requests
//
// On the other hand, the triple queue system has fewer requests coming in at the same time,
// so it doesn't need to handle as many requests concurrently.
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(
    elevator: Elevator,
    intervalMillis: Int,
    tripStatsCollector: ElevatorTripDataCollector
) = {

  import ElevatorStrategy._

  def acceptRequest[B <: Request](requested: Option[B]): Boolean = {
    requested.isDefined
  }

  def conditionallyAcceptRequest[B <: Request](
      requestQueue: TPriorityQueue[B],
      canElevatorAccept: Option[B] => Boolean
  ): ZIO[Any, Nothing, Option[B]] = {
    val requestCheck = for {
      maybeRequest <- requestQueue.peekOption
      result <-
        if (canElevatorAccept(maybeRequest)) requestQueue.take.as(maybeRequest)
        else STM.succeed(None)
    } yield result
    requestCheck.commit
  }

  def handleRequestBasedOnElevatorState[B <: Request]
      : TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, canElevatorAcceptRequest(elevator))

  def acceptInsideRequests(elevator: Elevator) =
    for {
      insideRequests <- elevator.insideRequests.takeAll.commit
      _ <- ZIO.succeed(
        insideRequests.map(request =>
          elevator.addFloorStop(request.withPickedByStatistics(elevator))
        )
      )
    } yield ()

  (for {

   // _ <- Console.printLine(logLine(elevator))

    _ <- ZIO.when(!elevator.floorStops.isEmpty)(
      Console.printLine(logLine(elevator))
    )

    _ <- acceptInsideRequests(elevator)

    // _ <- handleRequestBasedOnElevatorState(elevator.upRequests) flatMap {
    //   case Some(outsideUpRequest: OutsideUpRequest) =>
    //     ZIO.succeed(
    //       elevator.addFloorStop(
    //         outsideUpRequest.withPickedByStatistics(elevator)
    //       )
    //     )
    //   case _ =>
    //     ZIO.unit
    // }

    // _ <- handleRequestBasedOnElevatorState(elevator.downRequests) flatMap {
    //   case Some(outsideDownRequest: OutsideDownRequest) =>
    //     ZIO.succeed(
    //       elevator.addFloorStop(
    //         outsideDownRequest.withPickedByStatistics(elevator)
    //       )
    //     )
    //   case _ =>
    //     ZIO.unit
    // }
    


    reachedFloorStop <- ZIO.succeed {
      val reachedStop =
        elevator.floorStops.find(_.floor == elevator.currentFloor)
      reachedStop.map(reachedStop => {
        elevator.dequeueReachedFloorStop(reachedStop); reachedStop
      })
    }

    reachedStopWithTripData <- ZIO.succeed {
      reachedFloorStop.map((x:Request) => x.withDroppedOffAtStatistics(elevator.currentFloor))
    }

    _ <- ZIO.when(reachedStopWithTripData.nonEmpty){
      fileLogElevatorStats(reachedStopWithTripData.get) *>
      tripStatsCollector.addAll(
        reachedStopWithTripData.toList.map(request => request.elevatorTripData)
      )
    }

    _ <- ZIO.foreach(reachedStopWithTripData)(request => Console.printLine(s"------> $request") )

    _ <- ZIO.succeed(elevator.moveToFloor(calculateNextFloor(elevator)))

  } yield ()).repeat(Schedule.spaced(Duration.fromMillis(intervalMillis)))
  .catchAllDefect(t => Console.printLine(s"Caught defect: $t"))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Main extends ZIOAppDefault {

  // import zio2.elevator.IAsynchronousElevatorRequestHandler

  // private def program2 =
  //   for {
  //     x <- ZIO.service[IAsynchronousElevatorRequestHandler]
  //     _ <- x.startHandlingRequests
  //   } yield ()

  // def run = program2.provide(ElevatorRequestHandlerLayer.layer, ElevatorRequestWorkerLayer.layer())


  // def makeEmptyQueue[B <: Request : Ordering] = {
  //   TPriorityQueue.empty[B]
  // }

  val outsideUpRequestQueue = makeQueue[OutsideUpRequest]()
  val outsideDownRequestQueue = makeQueue[OutsideDownRequest]()


  // val queues = List()

  // val insideQueuesMap = List(
  //   ("elevator-1", makeEmptyQueue[InsideElevatorRequest]),
  //   ("elevator-2", makeEmptyQueue[InsideElevatorRequest])
  // )

  // val insideQueues = insideQueuesMap.map((id, zio) => zio)
  // private def prog = {

  //   for {

  //     up <- outsideUpRequestQueue

  //     down <- outsideDownRequestQueue

  //     elevators <- ZIO.foreach(insideQueuesMap)((id, zio) =>
  //       zio.map(iq => Elevator(id, up, down, iq))
  //     )

  //     v <- ZIO.foreach(insideQueuesMap)((id, zio) => zio)

  //     // layer <- ZIO.succeed(ElevatorRequestWorkerLayer.layer(v, up, down))

  //   } yield (elevators, v)

  // }

 // def runner = prog.provide(ElevatorRequestWorkerLayer.layer(insideQueues))

  private def program = {

    for {

      up <- outsideUpRequestQueue
      down <- outsideDownRequestQueue

      // elevators <- ZIO.foreach(
      //   List(
      //     makeQueue[InsideElevatorRequest](),
      //     makeQueue[InsideElevatorRequest]()
      //   )
      // )(zio => zio.map(insideQueue => Elevator("", up, down, insideQueue)))

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      // outsideUpRequestQueue <- makeQueue[OutsideUpRequest](
      //   OutsideUpRequest(14)
      // )

      // outsideDownRequestQueue <- makeQueue[OutsideDownRequest]()

      insidePassengerRequestQueueElevator1 <- makeQueue(
        InsideElevatorRequest(5)
        /*         InsideElevatorRequest(5, stat = RequestStatistic()),
        InsideElevatorRequest(-2, stat = RequestStatistic()) */
      )

      insidePassengerRequestQueueElevator2 <- makeQueue(
        InsideElevatorRequest(8)
      )

      // insidePassengerRequestQueueElevator2 <- makeQueue[InsideElevatorRequest]()

      // insidePassengerRequestQueueElevator3 <- makeQueue(
      //   InsideElevatorRequest(5, stat = RequestStatistic())
      // )

      // Creating outsideDownRequestQueue and scheduling it to be added in 10 seconds
      _ <- down
        .offer(OutsideDownRequest(1))
        .commit
        .delay(Duration.fromSeconds(10))
        .fork

      /*       _ <- insidePassengerRequestQueueElevator1
        .offer(InsideElevatorRequest(-5, stat = RequestStatistic()))
        .commit
        .delay(Duration.fromSeconds(2))
        .fork */

      // elevator #1
      elevator1 <- ZIO.succeed(
        elevator.Elevator(
          "1",
          up,
          down,
          insidePassengerRequestQueueElevator1
        )
      )

      // elevator #2
      elevator2 <- ZIO.succeed(
        elevator.Elevator(
          "2",
          up,
          down,
          insidePassengerRequestQueueElevator2
        )
      )

      // elevator #3
      // elevator3 <- ZIO.succeed(
      //   elevator.Elevator(
      //     "3",
      //     outsideUpRequestQueue,
      //     outsideDownRequestQueue,
      //     insidePassengerRequestQueueElevator3
      //   )
      // )

      tripDataCollector <- Ref
        .make(Vector[ElevatorTripData]())
        .map(storage => ElevatorTripDataCollector(storage))

      _ <- TripDataPublisher(tripDataCollector).run.fork

      _ <- ZIO.foreachParDiscard(List(elevator1, elevator2 /*, elevator3 */ ))(
        simulate(_, 1000, tripDataCollector).fork
      )

      _ <- ElevatorRequestHandler.start(
        up,
        down,
        elevator1.insideRequests,
        elevator2.insideRequests
        // elevator3.insideRequests
      ) raceFirst Console.readLine("Press any key to exit...\n")

    } yield ()
  }

  def run = program

}
