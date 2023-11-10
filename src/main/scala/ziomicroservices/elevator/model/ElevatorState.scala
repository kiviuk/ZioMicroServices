package ziomicroservices.elevator.model
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ElevatorState(elevatorId: String, currentFloor: CurrentFloor, destination: DestinationFloor)

object ElevatorState:
  given JsonEncoder[ElevatorState] = DeriveJsonEncoder.gen[ElevatorState]
  given JsonDecoder[ElevatorState] = DeriveJsonDecoder.gen[ElevatorState]