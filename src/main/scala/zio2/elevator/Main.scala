package zio2.elevator

import zio2.elevator.Request.makeQueue
import zio2.elevator

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

// TODO: Collect runtime statistics based on travel time, travel distance, averages, maximums, minimums...
//       - Every request created by the elevator system is
//         timestamped at creation, yielding the 'creation-time' [C] - a point in time.
//       - When the elevator picks-up a request the pick-up [P] time is recorded.
//       - Once the elevator reaches the requested floor the fulfilled-time [F] is recorded
//       - the total-time DT        : [F] - [C]
//       - the fulfillment-time DF  : [F] - [P]
//       - the waiting-time DW      : [P] - [C]
//       In short:
//       creation-time, pick-up-time, fulfilled-time are points in time
//       total-time, fulfillment-time, waiting-time are durations
//       - Log the number of floors covered, for ever fulfilled request
//       - Log every reached floor
//
//       1 logfile per elevator
//

// TODO: Problem: The elevator is constantly moving and needs to decide between multiple requests.
//       To solve this, a nearest next floor algorithm can be used.
//       However, a priority queue is needed that can re-prioritize depending on the elevator's current floor.
//       How about a self-reorganizing data structure for elevators? When the elevator is already heading up,
//       the next nearest floor destination becomes the head of the data structure, always adjusted for the
//       current elevator floor position. Every time the elevator reaches a floor,
//       that floor is removed from the data structure.
//       research: https://www.youtube.com/watch?v=6JxvKfSV9Ns
//       JGraphTs,  org.jgrapht.util
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(elevator: Elevator, intervalMillis: Int) = {

  def logStats(reachedStops: mutable.SortedSet[Request]) = {
    Files
      .writeLines(
        path = Path("logs.txt"),
        lines = reachedStops.map(req => s"${req.stats}").toList,
        charset = Charset.defaultCharset,
        openOptions = Set(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      )
      .catchAll(t => ZIO.succeed(println(t.getMessage)))
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

  def acceptRequest[B <: Request](requested: Option[B]): Boolean = {
    requested.isDefined
  }

  def canElevatorAcceptRequest[B <: Request](maybeRequest: Option[B]): Boolean =
    maybeRequest.exists { request =>
      elevator.determineElevatorState match {
        case ElevatorState.IDLE         => true
        case ElevatorState.HEADING_UP   => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case _                          => false
      }
    }

  def handleRequestBasedOnElevatorState[B <: Request]
      : TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, canElevatorAcceptRequest)

  def alwaysAcceptInsideRequest[B <: Request]
      : TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, acceptRequest)

  val colorMap: Map[String, String] =
    Map("1" -> GREEN, "2" -> BLUE, "3" -> YELLOW)

  val idColor = colorMap(elevator.id)

  val x = elevator.floorStops

  def y = if (x.nonEmpty)
    s"""floorRoute: "${x.mkString(", ")}", """
  else
    ""

  (for {

    _ <- ZIO.when(elevator.floorStops.isEmpty)(Console.printLine(
      s"${if (elevator.hasReachedStop) RED else idColor}{Elevator ${elevator.id}: $y" +
        s"""🏠 "${elevator.currentFloor}":D:${elevator.isHeadingDown}:U:${elevator.isHeadingUp} checking incoming queue${RESET}"""
    ))

    _ <- alwaysAcceptInsideRequest(elevator.insideRequests) flatMap {
      case Some(insideRequest: InsideElevatorRequest)
          if !elevator.floorStops.exists(r => r.floor == insideRequest.floor) =>
        ZIO.succeed(
          elevator.addFloorStop(insideRequest.withPickedByStatistics(elevator))
        )
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.upRequests) flatMap {
      case Some(outsideUpRequest: OutsideUpRequest)
          if !elevator.floorStops
            .exists(r => r.floor == outsideUpRequest.floor) =>
        ZIO.succeed(
          elevator.addFloorStop(
            outsideUpRequest.withPickedByStatistics(elevator)
          )
        )
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.downRequests) flatMap {
      case Some(outsideDownRequest: OutsideDownRequest)
          if !elevator.floorStops
            .exists(r => r.floor == outsideDownRequest.floor) =>
        ZIO.succeed(
          elevator.addFloorStop(
            outsideDownRequest.withPickedByStatistics(elevator)
          )
        )
      case _ =>
        ZIO.unit
    }

    reachedStops <- ZIO.succeed {
      val reachedStops =
        elevator.floorStops.filter(_.floor == elevator.currentFloor)
      elevator.floorStops --= reachedStops
      reachedStops.map(_.withDequeuedAt(elevator.currentFloor))
    }

    _ <- ZIO.succeed(elevator.moveToNextFloor())

    _ <- logStats(reachedStops)

  } yield ()).repeat(Schedule.spaced(Duration.fromMillis(intervalMillis)))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Main extends ZIOAppDefault {

  private def program = {

    val header =
      "servedByElevator;pickedUpOnFloor;destinationFloor;floorDistance;totalTime[ms];fulfillmentTime[ms];waitingTime[ms]"

    for {

      _ <- Files
        .writeLines(
          path = Path("logs.txt"),
          lines = List(header),
          charset = Charset.defaultCharset,
          openOptions = Set(StandardOpenOption.CREATE_NEW)
        )
        .catchAll(t => ZIO.succeed(println(t.getMessage)))

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- makeQueue[OutsideUpRequest](
        OutsideUpRequest(14, stats = RequestStatistics())
      )

      outsideDownRequestQueue <- makeQueue[OutsideDownRequest]()

      insidePassengerRequestQueueElevator1 <- makeQueue(
        InsideElevatorRequest(5, stats = RequestStatistics()),
        InsideElevatorRequest(-2, stats = RequestStatistics())
      )

      insidePassengerRequestQueueElevator2 <- makeQueue(
        InsideElevatorRequest(8, stats = RequestStatistics())
      )

      insidePassengerRequestQueueElevator3 <- makeQueue(
        InsideElevatorRequest(5, stats = RequestStatistics())
      )

      // Creating outsideDownRequestQueue and scheduling it to be added in 10 seconds
      _ <- outsideDownRequestQueue
        .offer(OutsideDownRequest(1, stats = RequestStatistics()))
        .commit
        .delay(Duration.fromSeconds(10))
        .fork

      _ <- insidePassengerRequestQueueElevator1
        .offer(InsideElevatorRequest(-5, stats = RequestStatistics()))
        .commit
        .delay(Duration.fromSeconds(2))
        .fork

      // elevator #1
      elevator1 <- ZIO.succeed(
        elevator.Elevator(
          "1",
          outsideUpRequestQueue,
          outsideDownRequestQueue,
          insidePassengerRequestQueueElevator1
        )
      )

      // elevator #2
      elevator2 <- ZIO.succeed(
        elevator.Elevator(
          "2",
          outsideUpRequestQueue,
          outsideDownRequestQueue,
          insidePassengerRequestQueueElevator2
        )
      )

      // elevator #3
      elevator3 <- ZIO.succeed(
        elevator.Elevator(
          "3",
          outsideUpRequestQueue,
          outsideDownRequestQueue,
          insidePassengerRequestQueueElevator3
        )
      )

      _ <- ZIO.foreachParDiscard(List(elevator1, elevator2, elevator3))(
        simulate(_, 1000).fork
      )

      _ <- ElevatorRequestHandler.start(
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        elevator1.insideRequests,
        elevator2.insideRequests,
        elevator3.insideRequests
      ) raceFirst Console.readLine("Press any key to exit...\n")

    } yield ()
  }

  def run = program

}
