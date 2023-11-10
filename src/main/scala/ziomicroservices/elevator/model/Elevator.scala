package ziomicroservices.elevator.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Elevator(id: String)

object Elevator:
  given JsonEncoder[Elevator] = DeriveJsonEncoder.gen[Elevator]
  given JsonDecoder[Elevator] = DeriveJsonDecoder.gen[Elevator]