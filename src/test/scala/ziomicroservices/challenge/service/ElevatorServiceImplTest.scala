package ziomicroservices.challenge.service

import zio.*
import zio.test.*
import zio.test.Assertion.equalTo
import ziomicroservices.challenge.service.elevator.{ElevatorService, ElevatorServiceImpl}
import ziomicroservices.challenge.model.Elevator

val testElevatorId: String = "123"

object ElevatorServiceTestImpl {
  private val elevators: Set[Elevator] = Set(Elevator(testElevatorId))
  val layer: ZLayer[Any, Nothing, ElevatorServiceImpl] = ZLayer.succeed(ElevatorServiceImpl(elevators))
}

object ElevatorServiceImplTest extends ZIOSpecDefault {

  def spec: Spec[Any, NoSuchElementException] = {

    val expectedElevator = Elevator(testElevatorId)

    suite("Elevator Service Tests")(
      test("Find Elevator by ID") {
        for {
          elevatorService <- ZIO.service[ElevatorService]
          actualElevator <- elevatorService.findElevatorById(testElevatorId)
        } yield {
          assert(actualElevator)(equalTo(expectedElevator))
        }
      }
    )
  }.provideLayer(
    ElevatorServiceTestImpl.layer
  )
}