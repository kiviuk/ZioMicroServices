package ziomicroservices.elevator.model

import zio.*
import zio.json.*
import zio.test.*

object ElevatorMetaTest extends ZIOSpecDefault {

  def spec = {
    val elevatorMeta = ElevatorState("1", CurrentFloor(1), DestinationFloor(2))
    suite("ElevatorMeta Encode / Decoder")(
      test("converts from class to json") {
        assertTrue(
          elevatorMeta.toJson == """{"elevatorId":"1","currentFloor":{"number":1},"destination":{"number":2}}"""
        )
      },
      test("converts from json to class") {
        assertTrue(
          """{"elevatorId":"1","currentFloor":{"number":1},"destination":{"number":2}}"""
            .fromJson[ElevatorState].getOrElse(null) == elevatorMeta
        )
      }
    )
  }
}