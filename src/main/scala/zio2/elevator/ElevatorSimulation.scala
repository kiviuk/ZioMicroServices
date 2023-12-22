package zio2.elevator

import zio.ZLayer
import zio.ZIO
import zio.stm.TPriorityQueue
import zio.stm.STM
import zio2.elevator.ElevatorLog.logLine
import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault}
import zio2.elevator.ElevatorLog.fileLogElevatorStats
import java.io.IOException

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import scala.collection.mutable
import scala.io.Source
object ElevatorStrategy:

  private def getNearestFloorStops(
      elevator: Elevator
  ): mutable.SortedSet[Request] =
    // define ordering based on closeness to currentFloor
    implicit val ordering: Ordering[Request] = (a: Request, b: Request) =>
      Math.abs(a.floor - elevator.currentFloor) - Math.abs(
        b.floor - elevator.currentFloor
      )
    // convert _floorStops to a SortedSet
    mutable.SortedSet(elevator.floorStops.toSeq*)

  def canElevatorAcceptRequest[B <: Request](
      elevator: Elevator
  )(maybeRequest: Option[B]): Boolean =
    maybeRequest.exists { request =>
      determineElevatorState(elevator) match
        case ElevatorState.IDLE         => true
        case ElevatorState.HEADING_UP   => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case _                          => false
    }

  def determineElevatorState(elevator: Elevator): ElevatorState =
    getNearestFloorStops(elevator).headOption match
      case Some(nextStop) if nextStop.floor > elevator.currentFloor =>
        ElevatorState.HEADING_UP
      case Some(nextStop) if nextStop.floor < elevator.currentFloor =>
        ElevatorState.HEADING_DOWN
      case Some(nextStop) if nextStop.floor == elevator.currentFloor =>
        ElevatorState.FLOOR_REACHED
      case None | _ => ElevatorState.IDLE

  def calculateNextFloor(elevator: Elevator): Int =
    determineElevatorState(elevator) match
      case ElevatorState.HEADING_UP   => elevator.currentFloor + 1
      case ElevatorState.HEADING_DOWN => elevator.currentFloor - 1
      case _                          => elevator.currentFloor

  def isHeadingUp(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.HEADING_UP

  def isHeadingDown(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.HEADING_DOWN

  def hasReachedStop(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.FLOOR_REACHED

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

  def acceptInsideRequests(elevator: Elevator) =
    for
      insideRequests <- elevator.insideQueue.takeAll.commit
      _ <- ZIO.succeed(
        insideRequests.map(request =>
          elevator.addFloorStop(request.withPickedByStatistics(elevator))
        )
      )
    yield ()

  def handleRequestBasedOnElevatorState[B <: Request](
      elevator: Elevator
  ): TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, canElevatorAcceptRequest(elevator))

trait SimulationTrait:

  def run(
      elevator: Elevator,
      intervalMillis: Duration,
      tripStatsCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit]

case class SimulationImpl(
    outsideUpRequests: TPriorityQueue[OutsideUpRequest],
    outsideDownRequests: TPriorityQueue[OutsideDownRequest]
) extends SimulationTrait:

  override def run(
      elevator: Elevator,
      duration: Duration,
      tripStatsCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit] =

    import ElevatorStrategy.*

    (for

      _ <- ZIO.when(!elevator.floorStops.isEmpty)(
        Console.printLine(logLine(elevator))
      )

      _ <- acceptInsideRequests(elevator)

      _ <- handleRequestBasedOnElevatorState(elevator)(outsideUpRequests)
        .flatMap {
          case Some(outsideUpRequest: OutsideUpRequest) =>
            ZIO.succeed(
              elevator.addFloorStop(
                outsideUpRequest.withPickedByStatistics(elevator)
              )
            )
          case _ =>
            ZIO.unit
        }

      _ <- handleRequestBasedOnElevatorState(elevator)(outsideDownRequests)
        .flatMap {
          case Some(outsideDownRequest: OutsideDownRequest) =>
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

      _ <- ZIO.foreach(reachedStopWithTripData) {request =>
        Console.printLine(s"${RED}REACHED => $request${RESET}") *>
        fileLogElevatorStats(request) *>
          tripStatsCollector.add(request.elevatorTripData)
      }

      _ <- ZIO.succeed(elevator.moveToFloor(calculateNextFloor(elevator)))
    yield ())
      .repeat(Schedule.spaced(duration))
      .catchAllDefect(t => Console.printLine(s"Caught defect: $t"))

object Simulation:
  def apply(
      outsideUpRequests: TPriorityQueue[OutsideUpRequest],
      outsideDownRequests: TPriorityQueue[OutsideDownRequest]
  ): SimulationImpl =
    SimulationImpl(outsideUpRequests, outsideDownRequests)
