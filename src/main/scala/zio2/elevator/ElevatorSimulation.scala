package zio2.elevator

import zio.stm.{TPriorityQueue, STM}
import zio.{Console, Schedule, Duration, ZIO, ZIOAppDefault, ZLayer}
import zio2.elevator.ElevatorLog.logLine
import zio2.elevator.ElevatorLog.fileLogElevatorStats

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import scala.collection.mutable
import scala.io.Source

enum ElevatorState(val abbreviation: String):
  case HEADING_UP extends ElevatorState("U")
  case HEADING_DOWN extends ElevatorState("D")
  case IDLE extends ElevatorState("I")
  case FLOOR_REACHED extends ElevatorState("R")

trait Simulation:

  def run(
      elevator: Elevator,
      periodicity: Duration,
      tripDataCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit]

end Simulation
case class SimulationImpl(
    outsideUpRequests: TPriorityQueue[OutsideUpRequest],
    outsideDownRequests: TPriorityQueue[OutsideDownRequest]
) extends Simulation:

  override def run(
      elevator: Elevator,
      periodicity: Duration,
      tripDataCollector: ElevatorTripDataCollector
  ): ZIO[Any, IOException, Long | Unit] =

    import ElevatorStrategy.*

    // fixme: accepting requests happens with the same periodictiy as the
    // simulation operates the elevator. The Process of accepting requests
    // could be decoupled from the simulation and run at much short intervals
    // On the flip side this would make it harder to think about the sequence
    // in which elevators processe the incoming requests. It could also lead
    // to starvation of other elevators. Starvation could be controlled
    // by limiting the number of active outside floorstops per elevator to 3.
    def acceptInsideRequests(elevator: Elevator) =
      for
        insideRequests <- elevator.insideQueue.takeAll.commit
        _ <- ZIO.foreach(insideRequests)(addFloorStopWithInfo)
      yield ()

    def addFloorStop[A <: Request](request: Option[A]) = request match
      case None          => ZIO.unit
      case Some(request) => addFloorStopWithInfo(request)

    def addFloorStopWithInfo[A <: Request](request: A) =
      ZIO.succeed(
        elevator.addFloorStop(request.withPickedByInfo(elevator))
      )

    (for

      _ <- ZIO.when(elevator.floorStops.nonEmpty)(
        Console.printLine(logLine(elevator))
      )

      _ <- acceptInsideRequests(elevator)

      _ <- acceptRequestBasedOnElevatorState[OutsideUpRequest](elevator)(
        outsideUpRequests
      ).flatMap(addFloorStop[OutsideUpRequest])

      _ <- acceptRequestBasedOnElevatorState[OutsideDownRequest](elevator)(
        outsideDownRequests
      ).flatMap(addFloorStop[OutsideDownRequest])

      reachedStopWithTripData <- ZIO.succeed {
        val reachedStop =
          elevator.floorStops.find(_.floor == elevator.currentFloor)
        reachedStop.map { reachedStop =>
          elevator.dequeueReachedFloorStop(reachedStop);
          reachedStop.withDroppedOffAtInfo(elevator.currentFloor)
        }
      }

      _ <- ZIO.foreach(reachedStopWithTripData) { request =>
        Console.printLine(s"${RED}REACHED => $request${RESET}") *>
          fileLogElevatorStats(request) *>
          tripDataCollector.add(request.tripData)
      }

      _ <- ZIO.succeed(elevator.moveToFloor(computeNextFloor(elevator)))
    yield ())
      .repeat(Schedule.spaced(periodicity))
      .catchAllDefect(t => Console.printLine(s"Caught defect: $t"))
end SimulationImpl

object Simulation:
  def apply(
      outsideUpRequests: TPriorityQueue[OutsideUpRequest],
      outsideDownRequests: TPriorityQueue[OutsideDownRequest]
  ): SimulationImpl =
    SimulationImpl(outsideUpRequests, outsideDownRequests)

object ElevatorStrategy:

  private def getClosestFloorStops(
      elevator: Elevator
  ): mutable.SortedSet[Request] =
    // define ordering based on closeness to currentFloor
    given Ordering[Request] = (a: Request, b: Request) =>
      Math.abs(a.floor - elevator.currentFloor) - Math.abs(
        b.floor - elevator.currentFloor
      )
    // convert _floorStops to SortedSet
    mutable.SortedSet(elevator.floorStops.toSeq*)

  def determineElevatorState(elevator: Elevator): ElevatorState =
    getClosestFloorStops(elevator).headOption match
      case Some(nextStop) if nextStop.floor > elevator.currentFloor =>
        ElevatorState.HEADING_UP
      case Some(nextStop) if nextStop.floor < elevator.currentFloor =>
        ElevatorState.HEADING_DOWN
      case Some(nextStop) if nextStop.floor == elevator.currentFloor =>
        ElevatorState.FLOOR_REACHED
      case None | _ => ElevatorState.IDLE

  def computeNextFloor(elevator: Elevator): Int =
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

  def isRequestInSameDirection[B <: Request](
      elevator: Elevator
  )(maybeRequest: Option[B]): Boolean =
    maybeRequest.exists { request =>
      determineElevatorState(elevator) match
        case ElevatorState.HEADING_UP   => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case ElevatorState.IDLE         => true
        case _                          => false
    }

  def conditionallyAcceptRequest[B <: Request](
      requestQueue: TPriorityQueue[B],
      canElevatorAcceptRequest: Option[B] => Boolean
  ): ZIO[Any, Nothing, Option[B]] =
    val maybeDequeue = for
      maybeRequest <- requestQueue.peekOption
      result <-
        if canElevatorAcceptRequest(maybeRequest) then
          requestQueue.take.as(maybeRequest)
        else STM.succeed(None)
    yield result

    maybeDequeue.commit

  def acceptRequestBasedOnElevatorState[B <: Request](
      elevator: Elevator
  ): TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, isRequestInSameDirection(elevator))
