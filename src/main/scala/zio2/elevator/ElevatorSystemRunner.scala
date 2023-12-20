package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault}
import zio.Console.{printLine, readLine}
import zio.ZLayer
import zio.ZIOAppDefault
import zio.Scope
import zio.ZIOAppArgs
import zio.http.Header.ContentSecurityPolicy.SourcePolicyType.`object-src`
import zio2.elevator.Request.{makeQueue, emptyQueue2}
import zio.Ref
import java.io.Console
import zio2.elevator.Request.emptyQueue2

case class X():
  val numberOfElevators = 1

  val outsideUpRequestQueueZIO = emptyQueue2[OutsideUpRequest]
  val outsideDownRequestQueueZIO = emptyQueue2[OutsideDownRequest]

  // Queues for inside elevator requests
  val elevatorInsideQueues =
    List.fill(numberOfElevators)(emptyQueue2[InsideElevatorRequest])

  // Elevator ids
  val elevatorIds = List.tabulate(numberOfElevators)(n => s"id-${n + 1}")

  // Preconfigured elevators with queues and ids
  val elevatorsZIO = for
    upQueue <- outsideUpRequestQueueZIO
    downQueue <- outsideDownRequestQueueZIO
    elevators <- ZIO.foreach(elevatorInsideQueues.zip(elevatorIds))(
      (insideQueueZIO, elevatorId) =>
        insideQueueZIO.map(insideQueue =>
          Elevator(elevatorId, upQueue, downQueue, insideQueue)
        )
    )
  yield elevators





  

object ElevatorSystemRunner extends ZIOAppDefault:

  // Queues for handling outside up and down elevator requests
  val outsideUpRequestQueueZIO: ZIO[Any, Nothing, TPriorityQueue[OutsideUpRequest]] = emptyQueue2[OutsideUpRequest]
  val outsideDownRequestQueueZIO: ZIO[Any, Nothing, TPriorityQueue[OutsideDownRequest]] = emptyQueue2[OutsideDownRequest]

  // Number of elevators
  val numberOfElevators = 1

  // Queues for inside elevator requests
  val elevatorInsideQueues =
    List.fill(numberOfElevators)(emptyQueue2[InsideElevatorRequest])

  // Elevator ids
  val elevatorIds = List.tabulate(numberOfElevators)(n => s"id-${n + 1}")

  // Preconfigured elevators with queues and ids
  val elevatorsZIO = for
    upQueue <- outsideUpRequestQueueZIO
    downQueue <- outsideDownRequestQueueZIO
    elevators <- ZIO.foreach(elevatorInsideQueues.zip(elevatorIds))(
      (insideQueueZIO, elevatorId) =>
        insideQueueZIO.map(insideQueue =>
          Elevator(elevatorId, upQueue, downQueue, insideQueue)
        )
    )
  yield elevators

  private def program =
    for
      elevators <- elevatorsZIO
      simulator <- ZIO.service[SimulatorTrait]
      asyncElevatorRequestHandler <- ZIO.service[AsyncElevatorRequestHandlerTrait]

      _ <- elevators(0).upRequests.offer(OutsideUpRequest(9)).commit
      _ <- elevators(0).downRequests.offer(OutsideDownRequest(1)).commit
      _ <- elevators(0).insideRequests.offer(InsideElevatorRequest(5)).commit

      x <- elevatorInsideQueues(0)
      _ <- x.offer(InsideElevatorRequest(6)).commit

      tripDataCollector <- Ref
        .make(Vector[ElevatorTripData]())
        .map(storage => ElevatorTripDataCollector(storage))

      _ <- TripDataPublisher(tripDataCollector).run.fork

      _ <- ZIO.foreachParDiscard(elevators)(simulator.simulate(_, 1000, tripDataCollector)).fork

      _ <- asyncElevatorRequestHandler.startHandlingRequests.raceFirst(readLine("Press any key to exit...\n"))
    yield ()

  val workerLayer = ZLayer.fromZIO {
    for
      insideQueues <- elevatorsZIO.map(elevatorList => elevatorList.map(elevator => elevator.insideRequests))
      outsideDownQueue <- elevatorsZIO.head.map(elevator => elevator.downRequests)
      outsideUpQueue <- elevatorsZIO.head.map(elevator => elevator.upRequests)
    yield ElevatorRequestWorkerLayer.layer(
      insideQueues,
      outsideUpQueue,
      outsideDownQueue
    )
  }

  override def run = program.provide(
    AsyncElevatorRequestHandlerLayer.layer,
    workerLayer,
    SimulatorLayer.layer
  )
