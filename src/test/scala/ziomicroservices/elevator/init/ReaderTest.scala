package ziomicroservices.elevator.init;

import zio.test.*
import zio.test.Assertion.*

object ReaderTest extends ZIOSpecDefault {

  def spec: Spec[Any, Throwable] = suite("Ingest JSON elevators") {
    test("elevatorsFromJsonFile should return set of elevators from json file") {
      val result = ElevatorFileReader.readElevators("/elevators.json")
      result match
        case  Left(errorMsg) =>
          throw new RuntimeException(s"Failed due to: $errorMsg")
        case Right(elevators) =>
          assert(elevators)(isNonEmpty)
    }
  }
}