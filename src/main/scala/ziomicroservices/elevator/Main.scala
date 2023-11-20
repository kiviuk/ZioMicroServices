package ziomicroservices.elevator

import zio.*
import zio.stm.{STM, TPriorityQueue, ZSTM}
import ziomicroservices.elevator.model.{InsideRequest, OutsideDownRequest, OutsideUpRequest, Request}
import ziomicroservices.elevator.model.{ElevatorCar, ElevatorState}
import ziomicroservices.elevator.service.elevator.Dispatcher


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
          case ElevatorState.UP => requested.floor > currentFloor // accept up requests only
          case ElevatorState.DOWN => requested.floor < currentFloor // accept down requests only
          case _ => false
      case _ => false
    }
  }

//  (for {
//    mayBeRequest <- incomingRequests.peekOption
//    _ <- if (canElevatorAcceptRequest(mayBeRequest)) incomingRequests.take else STM.succeed(())
//    result <- STM.succeed(mayBeRequest)
//  } yield result).commit

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

def simulate(elevatorCar: ElevatorCar, periodicity: Int) = {

  def conditionallyHandleRequestsForCurrentElevatorState[B <: Request]: TPriorityQueue[B] => IO[Nothing, Option[B]] =
    acceptRequestConditionally(_, elevatorCar.currentFloor, elevatorCar.determineElevatorState)

  (for {

    _ <- Console.printLine(
      s"{Elevator ${elevatorCar.id}:" +
        s""" current floorRoute: "${elevatorCar.floorStops.toList.mkString(",")}",""" +
        s""" current floor "${elevatorCar.currentFloor}": checking incoming queue"""
    ).orDie

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevatorCar.insideRequests) flatMap{
      case Some(insideRequest: InsideRequest) =>
        ZIO.succeed(elevatorCar.addFloorStop(insideRequest))
      case _ =>
        ZIO.unit
    }

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevatorCar.upRequests) flatMap {
      case Some(upwardRequest: OutsideUpRequest) =>
      ZIO.succeed(elevatorCar.addFloorStop(upwardRequest))
      case _ =>
        ZIO.unit
    }

    _ <- conditionallyHandleRequestsForCurrentElevatorState(elevatorCar.downRequests) flatMap {
      case Some(outsideDownRequest: OutsideDownRequest) =>
        ZIO.succeed(elevatorCar.addFloorStop(outsideDownRequest))
      case _ =>
        ZIO.unit
    }

    _ <- ZIO.succeed {
      if (elevatorCar.hasReachedStop) {
        println(s"{Elevator ${elevatorCar.id}: reached floor ${elevatorCar.currentFloor}}")
        elevatorCar.dequeueCurrentFloorStop()
      }
    }

    _ <- ZIO.succeed(elevatorCar.moveToNextFloor())

  } yield ()).repeat(Schedule.spaced(Duration.fromSeconds(periodicity)))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Main extends ZIOAppDefault {

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) => Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) => Request.requestOrdering.compare(x, y)

  implicit val insideRequestOrdering: Ordering[InsideRequest] =
    (x: InsideRequest, y: InsideRequest) => Request.requestOrdering.compare(x, y)

  def create[B <: Request: Ordering](request: B): UIO[TPriorityQueue[B]] = {
    TPriorityQueue.make[B](request).commit
  }

  private def program = {

    for {

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- create[OutsideUpRequest](OutsideUpRequest(14))
      insidePassengerRequestQueueElevator1 <- TPriorityQueue.make[InsideRequest](InsideRequest(3)).commit
      insidePassengerRequestQueueElevator2 <- TPriorityQueue.make[InsideRequest](InsideRequest(5)).commit

      // Creating outsideDownRequestQueue and scheduling an OutsideDownRequest to be added in 10 seconds
      outsideDownRequestQueue <- TPriorityQueue.make[OutsideDownRequest]().commit
      _ <- outsideDownRequestQueue.offer(OutsideDownRequest(1)).commit.delay(Duration.fromSeconds(10)).fork

      // elevator #1
      elevator1 <- ZIO.succeed(ElevatorCar(3333,
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        insidePassengerRequestQueueElevator1))

      // elevator #2
      elevator2 <- ZIO.succeed(ElevatorCar(4444,
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        insidePassengerRequestQueueElevator2))

      _ <- ZIO.foreachParDiscard(List(elevator1, elevator2))(simulate(_, 1).fork)

      _ <- ZIO.succeed(Dispatcher).fork

      _ <- Console.readLine("Press any key to exit...\n")

    } yield ()
  }

  def run = program

}