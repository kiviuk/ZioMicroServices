package ziomicroservices.elevator.model
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ElevatorMeta(elevator: Elevator, currentFloor: CurrentFloor, destination: DestinationFloor)

object ElevatorMeta:
  given JsonEncoder[ElevatorMeta] = DeriveJsonEncoder.gen[ElevatorMeta]
  given JsonDecoder[ElevatorMeta] = DeriveJsonDecoder.gen[ElevatorMeta]

