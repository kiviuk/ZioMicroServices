package ziomicroservices.elevator

import zio.*
import zio.stm.{STM, TPriorityQueue}
import ziomicroservices.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest, Request}
import ziomicroservices.elevator.model.{Elevator, ElevatorState}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def acceptRequestConditionally[B <: Request](incomingRequests: TPriorityQueue[B],
                                             currentFloor: Int,
                                             elevatorState: ElevatorState
                                            ): IO[Nothing, Option[B]] = {

  def canElevatorAcceptRequest(requested: Option[B]): Boolean = {
    requested match {
      case Some(requested: B) =>
        elevatorState match
          case ElevatorState.IDLE => true // accept any request while elevator is idling
          case ElevatorState.HEADING_UP => requested.floor > currentFloor // accept requests only
          case ElevatorState.HEADING_DOWN => requested.floor < currentFloor // accept down requests only
          case _ => false
      case _ => false
    }
  }

  {incomingRequests.peekOption.flatMap {
      mayBeRequest => {
        if (canElevatorAcceptRequest(mayBeRequest)) {
          incomingRequests.take.flatMap(_ => STM.succeed(mayBeRequest))
        } else {
          STM.succeed(None)
        }
      }
    }
  }.commit

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(elevator: Elevator, periodicity: Int) = {

  def conditionallyHandleRequestsForCurrentElevatorState[B <: Request]: TPriorityQueue[B] => IO[Nothing, Option[B]] =
    acceptRequestConditionally(_, elevator.currentFloor, elevator.determineElevatorState)

  (for {

    _ <- Console.printLine(
      s"{Elevator ${elevator.id}:" +
        s""" current floorRoute: "${elevator.floorStops.toList.mkString(",")}",""" +
        s""" current floor "${elevator.currentFloor}": checking incoming queue"""
    ).orDie

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevator.insideRequests) flatMap{
      case Some(insideRequest: InsideElevatorRequest) =>
        ZIO.succeed(elevator.addFloorStop(insideRequest))
      case _ =>
        ZIO.unit
    }

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevator.upRequests) flatMap {
      case Some(outsideUpRequest: OutsideUpRequest) =>
      ZIO.succeed(elevator.addFloorStop(outsideUpRequest))
      case _ =>
        ZIO.unit
    }

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevator.downRequests) flatMap {
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

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) => Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) => Request.requestOrdering.compare(x, y)

  implicit val insideRequestOrdering: Ordering[InsideElevatorRequest] =
    (x: InsideElevatorRequest, y: InsideElevatorRequest) => Request.requestOrdering.compare(x, y)

  def create[B <: Request: Ordering](request: B): UIO[TPriorityQueue[B]] = {
    TPriorityQueue.make[B](request).commit
  }

  private def program = {

    for {

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- create[OutsideUpRequest](OutsideUpRequest(14))
      insidePassengerRequestQueueElevator1 <- TPriorityQueue.make[InsideElevatorRequest](InsideElevatorRequest(3)).commit
      insidePassengerRequestQueueElevator2 <- TPriorityQueue.make[InsideElevatorRequest](InsideElevatorRequest(5)).commit

      // Creating outsideDownRequestQueue and scheduling an OutsideDownRequest to be added in 10 seconds
      outsideDownRequestQueue <- TPriorityQueue.make[OutsideDownRequest]().commit
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

      _ <- Dispatcher.start(
        List(elevator1.insideRequests, elevator2.insideRequests),
        outsideUpRequestQueue,
        outsideDownRequestQueue
      ).catchAll(ex => {
        println(ex)
        exitSignal.succeed(())
      }).fork

      _ <- (Console.readLine("Press any key to exit...\n") *> exitSignal.succeed(())) raceFirst exitSignal.await

    } yield ()
  }

  def run = program

}