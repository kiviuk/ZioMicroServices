package zio2.elevator

import zio.*
import zio.stm.{STM, TPriorityQueue}
import zio2.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest, Request}
import zio2.elevator.model.{Elevator, ElevatorState}


// TODO: Collect runtime statistics based on travel time, travel distance, averages, maximums, minimums...
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(elevator: Elevator, periodicity: Int) = {

  def conditionallyAcceptRequest[B <: Request](requestQueue: TPriorityQueue[B],
                                               canElevatorAccept: Option[B] => Boolean
                                              ): ZIO[Any, Nothing, Option[B]] = {
    val requestCheck = for {
      maybeRequest <- requestQueue.peekOption
      result <- if (canElevatorAccept(maybeRequest)) requestQueue.take.as(maybeRequest) else STM.succeed(None)
    } yield result
    requestCheck.commit
  }

  def acceptRequest[B <: Request](requested: Option[B]): Boolean = {
    requested.isDefined
  }

  def canElevatorAcceptRequest[B <: Request](maybeRequest: Option[B]): Boolean = maybeRequest.exists {
    request =>
      elevator.determineElevatorState match {
        case ElevatorState.IDLE => true
        case ElevatorState.HEADING_UP => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case _ => false
      }
  }

  def handleRequestBasedOnElevatorState[B <: Request]: TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, canElevatorAcceptRequest)

  def alwaysAcceptInsideRequest[B <: Request]: TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, acceptRequest)

  (for {

    _ <- Console.printLine(
      s"{Elevator ${elevator.id}:" +
        s""" current floorRoute: "${elevator.floorStops.toList.mkString(",")}",""" +
        s""" current floor "${elevator.currentFloor}": checking incoming queue"""
    ).orDie

    _ <- alwaysAcceptInsideRequest(elevator.insideRequests) flatMap {
      case Some(insideRequest: InsideElevatorRequest) =>
        ZIO.succeed(elevator.addFloorStop(insideRequest))
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.upRequests) flatMap {
      case Some(outsideUpRequest: OutsideUpRequest) =>
        ZIO.succeed(elevator.addFloorStop(outsideUpRequest))
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.downRequests) flatMap {
      case Some(outsideDownRequest: OutsideDownRequest) =>
        ZIO.succeed(elevator.addFloorStop(outsideDownRequest))
      case _ =>
        ZIO.unit
    }

    _ <- ZIO.succeed {
      if (elevator.hasReachedStop) {
        println(s"{Elevator ${elevator.id}: reached floor ${elevator.currentFloor}}")
        elevator.dequeueCurrentFloorStop()
      }
    }

    _ <- ZIO.succeed(elevator.moveToNextFloor())

  } yield ()).repeat(Schedule.spaced(Duration.fromSeconds(periodicity)))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Main extends ZIOAppDefault {

  import zio2.elevator.model.Request._
  
  private def program = {

    for {

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- makeQueue[OutsideUpRequest](OutsideUpRequest(14))
      insidePassengerRequestQueueElevator1 <- makeQueue(InsideElevatorRequest(3))
      insidePassengerRequestQueueElevator2 <- makeQueue(InsideElevatorRequest(5))

      // Creating outsideDownRequestQueue and scheduling an OutsideDownRequest to be added in 10 seconds
      outsideDownRequestQueue <- makeQueue[OutsideDownRequest]()
      _ <- outsideDownRequestQueue.offer(OutsideDownRequest(1)).commit.delay(Duration.fromSeconds(10)).fork

      // elevator #1
      elevator1 <- ZIO.succeed(Elevator(1,
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        insidePassengerRequestQueueElevator1))

      // elevator #2
      elevator2 <- ZIO.succeed(Elevator(2,
        outsideUpRequestQueue,
        outsideDownRequestQueue,

        insidePassengerRequestQueueElevator2))

      _ <- ZIO.foreachParDiscard(List(elevator1, elevator2))(simulate(_, 1).fork)

      exitSignal <- Promise.make[Nothing, Unit]

      _ <- ElevatorRequestHandler.start(
        List(elevator1.insideRequests, elevator2.insideRequests),
        outsideUpRequestQueue,
        outsideDownRequestQueue
      ).catchAll(ex => {
        println(ex)
        exitSignal.succeed(())
      }).fork

      _ <- (Console.readLine("Press any key to exit...\n")
        *> exitSignal.succeed(())) raceFirst exitSignal.await

    } yield ()
  }

  def run = program

}