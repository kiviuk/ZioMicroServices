package ziomicroservices.elevator.service.elevator

import zio.*
import ziomicroservices.elevator.init.ElevatorFileReader
import ziomicroservices.elevator.model.{CurrentFloor, DestinationFloor, Elevator, ElevatorMeta}

// INTERFACES
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
  val layer: ZLayer[Any, Throwable, ElevatorServiceImpl] =
    ElevatorFileReader.readElevators("/elevators.json")
    match
      case Left(error) => ZLayer.fail(new RuntimeException(error))
      case Right(elevators) => ZLayer.succeed(ElevatorServiceImpl(elevators))
}