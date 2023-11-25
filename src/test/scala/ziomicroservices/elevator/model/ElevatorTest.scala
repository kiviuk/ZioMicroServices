//package ziomicroservices.elevator.model
//
//import zio._
//import zio.test._
//import zio.json._
//
//object ElevatorTest extends ZIOSpecDefault {
//
//  def spec = {
//    suite("Elevator Encode / Decoder")(
//      test("converts from class to json") {
//        assertTrue(
//          Elevator("32").toJson == """{"id":"32"}"""
//        )
//      },
//      test("converts from json to class") {
//        assertTrue(
//          """{"id":"32"}""".fromJson[Elevator].getOrElse(null) == Elevator("32")
//        )
//      }
//    )
//  }
//}