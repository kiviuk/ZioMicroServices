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

object ElevatorSystemRunner extends ZIOAppDefault:
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

  // Number of elevators
  val N = 10

  // List of elevators, each with a unique ID and an inside request Channel
  val elevatorDataZIO: ZIO[Any, Nothing, ElevatorsAndChannels] = for
    outsideUpChannel <- emptyChannel[OutsideUpRequest]
    outsideDownChannel <- emptyChannel[OutsideDownRequest]

    insideChannels <- ZIO.foreach(1 to N)(_ =>
      emptyChannel[InsideElevatorRequest]
    )

    elevatorIds <- ZIO.foreach(1 to N)(n => ZIO.succeed(s"${n}"))

    elevators <- ZIO.foreach(elevatorIds.zip(insideChannels).toList)(
      (elevatorId, insideChannel) =>
        ZIO.succeed(Elevator(elevatorId, insideChannel))
    )
  yield ElevatorsAndChannels(elevators, outsideUpChannel, outsideDownChannel)

  private def program =
    for
      simulation <- ZIO.service[SimulationTrait]
      dispatcher <- ZIO.service[AsyncElevatorRequestHandlerTrait]
      elevators <- ZIO.service[List[Elevator]]

      _ <- zio.Console.printLine(s"${elevators.mkString("|")}")

      tripDataCollector <- Ref
        .make(Vector[ElevatorTripData]())
        .map(storage => ElevatorTripDataCollector(storage))

      _ <- TripDataPublisher(tripDataCollector).run.fork

      _ <- ZIO.foreachParDiscard(elevators)(
          simulation.run(_, Duration.fromMillis(1000L), tripDataCollector)
        ).fork

      _ <- dispatcher.startHandlingRequests.raceFirst(
        readLine("Press any key to exit...\n")
      )
    yield ()

  val outsideUpChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideUpChannel)
  }

  val outsideDownChannelLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.outsideDownChannel)
  }

  val elevatorLayer = ZLayer.fromZIO {
    elevatorDataZIO.map(_.elevators)
  }

  val workerLayer = ZLayer.fromZIO {
    (for
      elevators <- ZIO.service[List[Elevator]]
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield ElevatorRequestWorker(elevators, up, down))
  }

  val simulationLayer = ZLayer.fromZIO {
    (for
      up <- ZIO.service[TPriorityQueue[OutsideUpRequest]]
      down <- ZIO.service[TPriorityQueue[OutsideDownRequest]]
    yield Simulation(up, down))
  }

  override def run = program.provide(
    outsideUpChannelLayer,
    outsideDownChannelLayer,
    elevatorLayer,
    AsyncElevatorRequestHandlerLayer.layer,
    workerLayer,
    simulationLayer
  )
