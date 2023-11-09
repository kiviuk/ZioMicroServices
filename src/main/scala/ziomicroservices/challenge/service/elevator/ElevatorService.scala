package ziomicroservices.challenge.service.elevator

import zio.*
import ziomicroservices.challenge.model.{CurrentFloor, DestinationFloor, Elevator, ElevatorMeta}

// INTERFACEs
trait ElevatorService {
  def findElevator(elevator: Elevator): ZIO[ElevatorService, NoSuchElementException, Elevator]
  def findElevatorById(id: String): ZIO[ElevatorService, NoSuchElementException, Elevator]
}

object ElevatorService {
  def findElevator(elevator: Elevator): ZIO[ElevatorService, NoSuchElementException, Elevator] = {
    findElevatorById(elevator.id)
  }

  def findElevatorById(id: String): ZIO[ElevatorService, NoSuchElementException, Elevator] = {
    ZIO.serviceWithZIO[ElevatorService](_.findElevatorById(id))
  }
}

// IMPLEMENTATIONS
case class ElevatorServiceImpl(elevators: Set[Elevator]) extends ElevatorService {

  def findElevator(elevator: Elevator): ZIO[Any, NoSuchElementException, Elevator] = {
    findElevatorById(elevator.id)
  }

  def findElevatorById(id: String): ZIO[Any, NoSuchElementException, Elevator] = {

    val result = elevators.find(_.id == id)
    result match
      case Some(elevator) => ZIO.succeed(elevator)
      case None => ZIO.fail(new NoSuchElementException(s"No elevator with id $id found"))
  }
}

// LAYERS

object ElevatorServiceImpl {
  val layer: ZLayer[Any, Nothing, ElevatorServiceImpl] = ZLayer.succeed(ElevatorServiceImpl(Set.empty))
}

