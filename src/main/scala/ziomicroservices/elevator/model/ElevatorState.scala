package ziomicroservices.elevator.model
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

enum ElevatorState(val abbreviation: String):
  case HEADING_UP extends ElevatorState("U")
  case HEADING_DOWN extends ElevatorState("D")
  case IDLE extends ElevatorState("I")
  case HALT extends ElevatorState("H")

object ElevatorState:
  given JsonEncoder[ElevatorState] = DeriveJsonEncoder.gen[ElevatorState]
  given JsonDecoder[ElevatorState] = DeriveJsonDecoder.gen[ElevatorState]