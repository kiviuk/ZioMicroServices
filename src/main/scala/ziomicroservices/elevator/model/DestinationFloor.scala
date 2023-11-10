package ziomicroservices.elevator.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class DestinationFloor(number: Int)

object DestinationFloor:
  given JsonEncoder[DestinationFloor] = DeriveJsonEncoder.gen[DestinationFloor]
  given JsonDecoder[DestinationFloor] = DeriveJsonDecoder.gen[DestinationFloor]