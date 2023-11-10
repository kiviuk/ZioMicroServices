package ziomicroservices.elevator.init;

import zio.test.*
import zio.test.Assertion.*

object ReaderTest extends ZIOSpecDefault {

  def spec: Spec[Any, Throwable] = suite("Ingest JSON elevator data") {
    test("should return set of elevators from json file") {
      val result = ElevatorFileReader.readElevators("/elevators.json")
      result match
        case  Left(errorMsg) =>
          throw new RuntimeException(s"Failed to load elevators due to: $errorMsg")
        case Right(elevators) =>
          assert(elevators)(isNonEmpty)
    }

    test("should return set of elevator state data from json file") {
      val result = ElevatorFileReader.readElevatorStates("/elevatorStates.json")
      result match
        case Left(errorMsg) =>
          throw new RuntimeException(s"Failed to load elevator states due to: $errorMsg")
        case Right(elevators) =>
          assert(elevators)(isNonEmpty)
    }

  }
}