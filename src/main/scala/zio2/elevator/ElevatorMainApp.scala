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

object ElevatorMainApp extends ZIOAppDefault:

  import ElevatorConfig.periodicity
  import ElevatorConfig.N

  // FIX ME: Allow for dynamic addition of elevators based on load.
  private def program =
    for
      simulation <- ZIO.service[Simulation]
      dispatcher <- ZIO.service[DispatchLoop]
      elevators <- ZIO.service[List[Elevator]]

      _ <- zio.Console.printLine(
        s"MainApp ia starting $N elevators:\n${elevators.mkString("\n")}"
      )

      tripDataCollector <- Ref
        .make(Vector[ElevatorTripData]())
        .map(storage => ElevatorTripDataCollector(storage))

      _ <- TripDataPublisher(tripDataCollector).run.fork

      _ <- ZIO
        .foreachParDiscard(elevators)(
          simulation.run(_, periodicity, tripDataCollector)
        )
        .fork

      _ <- dispatcher.startHandlingRequests.raceFirst(
        readLine("Press any key to exit...\n")
      )
    yield ()

  import Layers._
  override def run = program.provide(
    outsideUpChannelLayer,
    outsideDownChannelLayer,
    dispatchLoopLayer,
    dispatcherLayer,
    elevatorLayer,
    simulationLayer
  )
end ElevatorMainApp

object ElevatorConfig:
  // Number of elevators
  val N = 10
  val periodicity = Duration.fromMillis(1L)
  case class ElevatorsAndChannels(
      private val _elevators: List[Elevator],
      private val _outsideUpChannel: TPriorityQueue[OutsideUpRequest],
      private val _outsideDownChannel: TPriorityQueue[OutsideDownRequest]
  ) {
    def insideChannel(n: Int) = _elevators(n).insideQueue
    def insideChannels = _elevators.map(_.insideQueue)
    def elevators = _elevators
    def outsideUpChannel = _outsideUpChannel
    def outsideDownChannel = _outsideDownChannel
  }

  // List of elevators, each with a unique ID and an inside request Channel
  val elevatorDataZIO: ZIO[Any, Nothing, ElevatorsAndChannels] = for
    outsideUpChannel <- emptyChannel[OutsideUpRequest]
    outsideDownChannel <- emptyChannel[OutsideDownRequest]

    insideChannels <- ZIO.foreach(1 to N)(_ =>
      emptyChannel[InsideElevatorRequest]
    )

    elevators <- ZIO.foreach((1 to N).zip(insideChannels).toList)(
      (elevatorId, insideChannel) =>
        ZIO.succeed(Elevator(s"$elevatorId}", insideChannel))
    )
  yield ElevatorsAndChannels(elevators, outsideUpChannel, outsideDownChannel)
end ElevatorConfig

object Layers:
  import ElevatorConfig._

  val outsideUpChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideUpChannel)
  }

  val outsideDownChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideDownChannel)
  }

  val elevatorLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.elevators)
  }

  val dispatchLoopLayer = ZLayer.fromZIO {
    for {
      dispatcher <- ZIO.service[Dispatcher]
    } yield DispatchLoop(dispatcher)
  }

  val dispatcherLayer = ZLayer.fromZIO {
    (for
      elevators <- ZIO.service[List[Elevator]]
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield Dispatcher(elevators, up, down))
  }

  val simulationLayer = ZLayer.fromZIO {
    (for
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield Simulation(up, down))
  }
end Layers
