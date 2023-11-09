package ziomicroservices.challenge.service

import zio.*
import zio.test.*
import zio.test.Assertion.{equalTo, hasMessage, isSubtype, fails}
import ziomicroservices.challenge.service.elevator.{ElevatorService, ElevatorServiceImpl}
import ziomicroservices.challenge.model.Elevator

object ElevatorServiceImplTest extends ZIOSpecDefault {

  case class TestConfig(testElevatorId: String)

  private val happyId = "123"
  private val sadId = "0"

  object MockElevatorService {
    private val elevators: Set[Elevator] = Set(Elevator(happyId))
    val layer: ZLayer[Any, Nothing, ElevatorService] =
      ZLayer.succeed(ElevatorServiceImpl(elevators))
  }

  val happyLayer: ZLayer[Any, Nothing, ElevatorService with TestConfig] =
    MockElevatorService.layer ++ ZLayer.succeed(TestConfig(happyId))

  val sadLayer: ZLayer[Any, Nothing, ElevatorService with TestConfig] =
    MockElevatorService.layer ++ ZLayer.succeed(TestConfig(sadId))

  def spec: Spec[Any, NoSuchElementException] = suite("Elevator Service Tests") {

    test("Should find the elevator by its ID.") {
      for {
        mockElevatorService <- ZIO.service[ElevatorService]
        testId <- ZIO.service[TestConfig].map(config => config.testElevatorId)
        actualElevator <- mockElevatorService.findElevatorById(testId)
        result <- assert(actualElevator)(equalTo(Elevator(happyId)))
      } yield result
    }.provideLayer(
      happyLayer)

    test("Should throw an error when elevator does not exist.") {
      for {
        mockElevatorService <- ZIO.service[ElevatorService]
        testId <- ZIO.service[TestConfig].map(config => config.testElevatorId)
        exit <- mockElevatorService.findElevatorById(testId).exit
      } yield assert(exit)(
        fails(
          isSubtype[NoSuchElementException](
            hasMessage(
              equalTo(s"No elevator with id ${sadId} found")
            )
          )
        )
      )
    }.provideLayer(
      sadLayer)
  }
}