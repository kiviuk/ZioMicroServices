package ziomicroservices.elevator.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class CurrentFloor(number: Int)

object CurrentFloor:
  given JsonEncoder[CurrentFloor] = DeriveJsonEncoder.gen[CurrentFloor]
  given JsonDecoder[CurrentFloor] = DeriveJsonDecoder.gen[CurrentFloor]