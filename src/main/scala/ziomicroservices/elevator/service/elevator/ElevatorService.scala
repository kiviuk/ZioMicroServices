package ziomicroservices.elevator.service.elevator

import zio.*
import ziomicroservices.elevator.init.ElevatorFileReader
import ziomicroservices.elevator.model.{CurrentFloor, DestinationFloor, Elevator, ElevatorState}

// INTERFACES
trait ElevatorService {
  def findElevator(elevator: Elevator): ZIO[ElevatorService, NoSuchElementException, Elevator]

  def findElevatorById(id: String): ZIO[ElevatorService, NoSuchElementException, Elevator]

  def findElevatorStateByElevatorId(elevatorId: String): ZIO[ElevatorService, NoSuchElementException, ElevatorState]
}

object ElevatorService {
  def findElevator(elevator: Elevator): ZIO[ElevatorService, NoSuchElementException, Elevator] = {
    findElevatorById(elevator.id)
  }

  def findElevatorById(id: String): ZIO[ElevatorService, NoSuchElementException, Elevator] = {
    ZIO.serviceWithZIO[ElevatorService](_.findElevatorById(id))
  }

  def findElevatorStateByElevatorId(elevatorId: String): ZIO[ElevatorService, NoSuchElementException, ElevatorState] = {
    ZIO.serviceWithZIO[ElevatorService](_.findElevatorStateByElevatorId(elevatorId))
  }
}

// IMPLEMENTATIONS
case class ElevatorServiceImpl(elevators: Set[Elevator], elevatorStates: Set[ElevatorState]) extends ElevatorService {

  def findElevator(elevator: Elevator): ZIO[Any, NoSuchElementException, Elevator] = {
    findElevatorById(elevator.id)
  }

  def findElevatorById(id: String): ZIO[Any, NoSuchElementException, Elevator] = {

    val result = elevators.find(_.id == id)
    result match
      case Some(elevator) => ZIO.succeed(elevator)
      case None => ZIO.fail(new NoSuchElementException(s"No elevator with id $id found."))
  }

  def findElevatorStateByElevatorId(elevatorId: String): ZIO[Any, NoSuchElementException, ElevatorState] = {
    elevatorStates.find(_.elevatorId == elevatorId) match
      case Some(elevatorState) => ZIO.succeed(elevatorState)
      case None => ZIO.fail(new NoSuchElementException(s"No elevator state with id $elevatorId found."))
  }
}

// LAYERS

object ElevatorServiceImpl {

  private val stringOrElevators = ElevatorFileReader.readElevators("/elevators.json")
  private val stringOrStates = ElevatorFileReader.readElevatorStates("/elevatorStates.json")

  private val importElevators: IO[Throwable, Set[Elevator]] = ZIO.fromEither(stringOrElevators)
  private val importStates: IO[Throwable, Set[ElevatorState]] = ZIO.fromEither(stringOrStates)

  var layer: ZLayer[Any, Throwable, ElevatorServiceImpl] = ZLayer.fromZIO(
    for {
      elevators <- importElevators
      states <- importStates
    } yield ElevatorServiceImpl(elevators, states)
  )

  //
  //  val layer: ZLayer[Any, Throwable, ElevatorServiceImpl] =
  //    (
  //      ElevatorFileReader.readElevators("/elevators.json"),
  //      ElevatorFileReader.readElevatorStates("/elevatorStates.json")
  //    ) match
  //      case (Right(elevators), Right(elevatorStates)) => ZLayer.succeed(ElevatorServiceImpl(elevators, elevatorStates))
  //      case (Left(error), _) => ZLayer.fail(new RuntimeException(s"Loading elevators failed due to $error"))
  //      case (_, Left(error)) => ZLayer.fail(new RuntimeException(s"Loading elevator states failed due to $error"))
}