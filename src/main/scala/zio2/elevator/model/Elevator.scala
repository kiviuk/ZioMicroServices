package zio2.elevator.model

import zio.stm.TPriorityQueue
import zio2.elevator.model.ElevatorState.IDLE

import scala.collection.mutable

trait Elevator {
  def id: Int

  def upRequests: TPriorityQueue[OutsideUpRequest]

  def downRequests: TPriorityQueue[OutsideDownRequest]

  def insideRequests: TPriorityQueue[InsideElevatorRequest]

  def floorStops: mutable.SortedSet[Request]

  def currentFloor: Int

  def hasReachedStop: Boolean

  def dequeueCurrentFloorStop(): Unit

  def moveToNextFloor(): Unit

  def addFloorStop(request: Request): Unit

  def determineElevatorState: ElevatorState

  def isHeadingUp(elevator: Elevator): Boolean

  def isHeadingDown(elevator: Elevator): Boolean


}

case class ElevatorImpl(_id: Int,
                        _outsideUpRequests: TPriorityQueue[OutsideUpRequest],
                        _outsideDownRequests: TPriorityQueue[OutsideDownRequest],
                        _insideRequests: TPriorityQueue[InsideElevatorRequest],
                        _floorStops: mutable.SortedSet[Request] = mutable.SortedSet()) extends Elevator {

  private var _currentFloor: Int = 0

  override def id: Int = _id

  override def upRequests: TPriorityQueue[OutsideUpRequest] = _outsideUpRequests

  override def downRequests: TPriorityQueue[OutsideDownRequest] = _outsideDownRequests

  override def insideRequests: TPriorityQueue[InsideElevatorRequest] = _insideRequests

  override def floorStops: mutable.SortedSet[Request] = _floorStops

  override def currentFloor: Int = _currentFloor

  override def addFloorStop(request: Request): Unit = _floorStops.add(request)

//  override def hasReachedStop: Boolean = this.determineElevatorState match
//    case ElevatorState.IDLE | ElevatorState.HALT => true
//    case _ => false

  override def hasReachedStop: Boolean =
    _floorStops.headOption match {
    case Some(nextStop: Request) => _currentFloor == nextStop.floor
    case _ => false
  }

  override def dequeueCurrentFloorStop(): Unit = _floorStops -= _floorStops.head

  override def determineElevatorState: ElevatorState =
    _floorStops.headOption match {
      case Some(nextStop) if nextStop.floor > _currentFloor => ElevatorState.HEADING_UP
      case Some(nextStop) if nextStop.floor < _currentFloor => ElevatorState.HEADING_DOWN
      case Some(nextStop) if (nextStop.floor == _currentFloor) && _floorStops.nonEmpty => ElevatorState.HALT
      case Some(nextStop) if (nextStop.floor == _currentFloor) && _floorStops.isEmpty => ElevatorState.IDLE
      case None | _ => ElevatorState.IDLE
    }

  override def moveToNextFloor(): Unit = {
    _currentFloor += (
      determineElevatorState match
        case ElevatorState.HEADING_UP => 1
        case ElevatorState.HEADING_DOWN => -1
        case _ => 0
      )
  }

  override def isHeadingUp(elevator: Elevator): Boolean = this.determineElevatorState == ElevatorState.HEADING_UP

  override def isHeadingDown(elevator: Elevator): Boolean = this.determineElevatorState == ElevatorState.HEADING_DOWN

}

object Elevator {

  def apply(id: Int, outsideUpRequests: TPriorityQueue[OutsideUpRequest],
            outsideDownRequests: TPriorityQueue[OutsideDownRequest],
            insideElevatorRequests: TPriorityQueue[InsideElevatorRequest]): ElevatorImpl =
    ElevatorImpl(id, outsideUpRequests, outsideDownRequests, insideElevatorRequests)
}