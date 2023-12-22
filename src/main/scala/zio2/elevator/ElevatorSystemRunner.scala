package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault}
import zio.Console.{printLine, readLine}
import zio.ZLayer
import zio.ZIOAppDefault
import zio.Scope
import zio.ZIOAppArgs
import zio2.elevator.Request.{makeChannel, emptyChannel}
import zio.Ref
import java.io.Console
import zio2.elevator.Request.emptyChannel

object ElevatorSystemRunner extends ZIOAppDefault:

  // Number of elevators
  val numberOfElevators = 1

  case class ElevatorsAndChannels(
    private val _elevators: List[Elevator],
    private val _outsideUpChannel: TPriorityQueue[OutsideUpRequest],
    private val _outsideDownChannel: TPriorityQueue[OutsideDownRequest]) {

      def insideChannel(n: Int) = _elevators(n).insideChannel 
      def insideChannels = _elevators.map(_.insideChannel)
      def elevators = _elevators
      def outsideUpChannel = _outsideUpChannel
      def outsideDownChannel = _outsideDownChannel
    }

  // List of elevators, each with a unique ID and an inside request Channel
  val elevatorDataZIO: ZIO[Any, Nothing, ElevatorsAndChannels] = for
    outsideUpChannel <- emptyChannel[OutsideUpRequest]
    outsideDownChannel <- emptyChannel[OutsideDownRequest]
    insideChannels <- ZIO.foreach(List(numberOfElevators))(n => emptyChannel[InsideElevatorRequest])
    elevatorIds <- ZIO.succeed(List.tabulate(numberOfElevators)(n => s"${n + 1}"))
    elevators <- ZIO.foreach(insideChannels.zip(elevatorIds))(
      (insideChannel, elevatorId) => ZIO.succeed(Elevator(elevatorId, insideChannel))
    )
  yield ElevatorsAndChannels(elevators, outsideUpChannel, outsideDownChannel)

  private def program =
    for
      simulator <- ZIO.service[SimulatorTrait]
      dispatcher <- ZIO.service[AsyncElevatorRequestHandlerTrait]
      elevatorData <- elevatorDataZIO

      tripDataCollector <- Ref
        .make(Vector[ElevatorTripData]())
        .map(storage => ElevatorTripDataCollector(storage))

      _ <- TripDataPublisher(tripDataCollector).run.fork

      _ <- ZIO.foreachParDiscard(elevatorData.elevators)(simulator.simulate(_, 1000, tripDataCollector)).fork

      _ <- dispatcher.startHandlingRequests.raceFirst(readLine("Press any key to exit...\n"))
    yield ()

  val outsideUpChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideUpChannel)
  } 

  val outsideDownChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideDownChannel)
  } 

  val workerLayer = ZLayer.fromZIO {
    (for
      elevatorData <- elevatorDataZIO
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield ElevatorRequestWorker(elevatorData.elevators, up, down))
  }

  val simulationLayer = ZLayer.fromZIO {
    (for 
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield Simulator(up, down))
  }

  override def run = program.provide(
    outsideUpChannelLayer,
    outsideDownChannelLayer,
    AsyncElevatorRequestHandlerLayer.layer,
    workerLayer,
    simulationLayer
  )
