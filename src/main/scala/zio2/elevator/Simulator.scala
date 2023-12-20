package zio2.elevator

import zio.ZLayer
import zio.ZIO
import zio.stm.TPriorityQueue
import zio.stm.STM
import zio2.elevator.ElevatorLog.logLine
import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault}
import zio2.elevator.ElevatorLog.fileLogElevatorStats
import java.io.IOException

trait SimulatorTrait:

  def simulate(
    elevator: Elevator,
    intervalMillis: Int,
    tripStatsCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit]

case class SimulatorImpl() extends SimulatorTrait:

  override def simulate(
    elevator: Elevator,
    intervalMillis: Int,
    tripStatsCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit] =

    import ElevatorStrategy.*

    def conditionallyAcceptRequest[B <: Request](
      requestQueue: TPriorityQueue[B],
      canElevatorAccept: Option[B] => Boolean
    ): ZIO[Any, Nothing, Option[B]] =

      val requestCheck = for
        maybeRequest <- requestQueue.peekOption
        result <-
          if canElevatorAccept(maybeRequest) then
            requestQueue.take.as(maybeRequest)
          else STM.succeed(None)
      yield result

      requestCheck.commit

    def handleRequestBasedOnElevatorState[B <: Request]
      : TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
      conditionallyAcceptRequest(_, canElevatorAcceptRequest(elevator))

    def acceptInsideRequests(elevator: Elevator) =
      for
        insideRequests <- elevator.insideRequests.takeAll.commit
        _ <- ZIO.succeed(
          insideRequests.map(request =>
            elevator.addFloorStop(request.withPickedByStatistics(elevator))
          )
        )
      yield ()

    (for
    //   _ <- Console.printLine(logLine(elevator))

      _ <- ZIO.when(!elevator.floorStops.isEmpty)(
        Console.printLine(logLine(elevator))
      )

      _ <- acceptInsideRequests(elevator)

      _ <- handleRequestBasedOnElevatorState(elevator.upRequests).flatMap {
        case Some(outsideUpRequest: OutsideUpRequest) =>
          println("OutsideUpRequest")  
          ZIO.succeed(
            elevator.addFloorStop(
              outsideUpRequest.withPickedByStatistics(elevator)
            )
          )
        case _ =>
          ZIO.unit
      }

      _ <- handleRequestBasedOnElevatorState(elevator.downRequests).flatMap {
        case Some(outsideDownRequest: OutsideDownRequest) =>
          println("OutsideDownRequest")  
          ZIO.succeed(
            elevator.addFloorStop(
              outsideDownRequest.withPickedByStatistics(elevator)
            )
          )
        case _ =>
          ZIO.unit
      }

      reachedFloorStop <- ZIO.succeed {
        val reachedStop =
          elevator.floorStops.find(_.floor == elevator.currentFloor)
        reachedStop.map { reachedStop =>
          elevator.dequeueReachedFloorStop(reachedStop); reachedStop
        }
      }

      reachedStopWithTripData <- ZIO.succeed {
        reachedFloorStop.map((request: Request) =>
          request.withDroppedOffAtStatistics(elevator.currentFloor)
        )
      }

      _ <- ZIO.when(reachedStopWithTripData.nonEmpty) {
        fileLogElevatorStats(reachedStopWithTripData.get) *>
          tripStatsCollector.addAll(
            reachedStopWithTripData.toList.map(request =>
              request.elevatorTripData
            )
          )
      }

      _ <- ZIO.foreach(reachedStopWithTripData)(request =>
        Console.printLine(s"------> $request")
      )

      _ <- ZIO.succeed(elevator.moveToFloor(calculateNextFloor(elevator)))
    yield ())
      .repeat(Schedule.spaced(Duration.fromMillis(intervalMillis)))
      .catchAllDefect(t => Console.printLine(s"Caught defect: $t"))

object SimulatorLayer:
  def layer: ZLayer[Any, Nothing, SimulatorTrait] =
    ZLayer.fromZIO(ZIO.succeed(SimulatorImpl()))
