package zio2.elevator

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio2.elevator.ElevatorState

enum ElevatorState(val abbreviation: String):
  case HEADING_UP extends ElevatorState("U")
  case HEADING_DOWN extends ElevatorState("D")
  case IDLE extends ElevatorState("I")
  case FLOOR_REACHED extends ElevatorState("R")

object ElevatorState:
  given JsonEncoder[ElevatorState] = DeriveJsonEncoder.gen[ElevatorState]
  given JsonDecoder[ElevatorState] = DeriveJsonDecoder.gen[ElevatorState]