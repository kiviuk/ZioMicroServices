package zio2.elevator

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import scala.collection.mutable
import scala.io.Source

object ElevatorStrategy {

  def getSortedFloorStops(elevator: Elevator): mutable.SortedSet[Request] = {

    // define ordering based on closeness to currentFloor
    implicit val ordering: Ordering[Request] = (a: Request, b: Request) => {
      Math.abs(a.floor - elevator.currentFloor) - Math.abs(
        b.floor - elevator.currentFloor
      )
    }

    // convert _floorStops to a SortedSet
    mutable.SortedSet(elevator.floorStops.toSeq: _*)
  }

  def determineElevatorState(elevator: Elevator): ElevatorState =
    getSortedFloorStops(elevator).headOption match {
      case Some(nextStop) if nextStop.floor > elevator.currentFloor =>
        ElevatorState.HEADING_UP
      case Some(nextStop) if nextStop.floor < elevator.currentFloor =>
        ElevatorState.HEADING_DOWN
      case Some(nextStop) if nextStop.floor == elevator.currentFloor =>
        ElevatorState.FLOOR_REACHED
      case None | _ => ElevatorState.IDLE
    }

  def calculateNextFloor(elevator: Elevator): Int = {
    determineElevatorState(elevator) match
      case ElevatorState.HEADING_UP   => elevator.currentFloor + 1
      case ElevatorState.HEADING_DOWN => elevator.currentFloor - 1
      case _                          => elevator.currentFloor
  }
  
  def canElevatorAcceptRequest[B <: Request](elevator: Elevator)(maybeRequest: Option[B]): Boolean =
    maybeRequest.exists { request =>
      determineElevatorState(elevator) match {
        case ElevatorState.IDLE         => true
        case ElevatorState.HEADING_UP   => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case _                          => false
      }
    }

  def isHeadingUp(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.HEADING_UP

  def isHeadingDown(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.HEADING_DOWN

  def hasReachedStop(elevator: Elevator): Boolean =
    determineElevatorState(elevator) == ElevatorState.FLOOR_REACHED

}
