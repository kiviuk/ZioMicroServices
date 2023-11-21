package ziomicroservices.elevator.model

import zio.*

//
//import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
//
//final case class Elevator(id: String)
//
//object Elevator:
//  given JsonEncoder[Elevator] = DeriveJsonEncoder.gen[Elevator]
//  given JsonDecoder[Elevator] = DeriveJsonDecoder.gen[Elevator]

import zio.*
import zio.stm.{STM, TPriorityQueue}

import java.time.Instant
import scala.collection.mutable

trait Elevator {
  def id: Int

  def upRequests: TPriorityQueue[OutsideUpRequest]

  def downRequests: TPriorityQueue[OutsideDownRequest]

  def insideRequests: TPriorityQueue[InsideRequest]

  def floorStops: mutable.SortedSet[Request]

  def currentFloor: Int

  def hasReachedStop: Boolean

  def dequeueCurrentFloorStop(): Unit

  def moveToNextFloor(): Unit

  def addFloorStop(request: Request): Unit

  def determineElevatorState: ElevatorState

}

case class ElevatorImpl(_id: Int,
                        _outsideUpRequests: TPriorityQueue[OutsideUpRequest],
                        _outsideDownRequests: TPriorityQueue[OutsideDownRequest],
                        _insideRequests: TPriorityQueue[InsideRequest],
                        _floorStops: mutable.SortedSet[Request] = mutable.SortedSet()) extends Elevator {

  private var _currentFloor: Int = 0

  override def id: Int = _id

  override def upRequests: TPriorityQueue[OutsideUpRequest] = _outsideUpRequests

  override def downRequests: TPriorityQueue[OutsideDownRequest] = _outsideDownRequests

  override def insideRequests: TPriorityQueue[InsideRequest] = _insideRequests

  override def floorStops: mutable.SortedSet[Request] = _floorStops

  override def currentFloor: Int = _currentFloor

  override def addFloorStop(request: Request): Unit = _floorStops.add(request)

  override def hasReachedStop: Boolean = _floorStops.headOption match {
    case Some(nextStop: Request) => _currentFloor == nextStop.floor
    case _ => false
  }

  override def dequeueCurrentFloorStop(): Unit = _floorStops -= _floorStops.head

  override def determineElevatorState: ElevatorState =
    _floorStops.headOption match {
      case Some(request) if request.floor > _currentFloor => ElevatorState.UP
      case Some(request) if request.floor < _currentFloor => ElevatorState.DOWN
      case Some(request) if (request.floor == _currentFloor) && _floorStops.nonEmpty => ElevatorState.HALT
      case Some(request) if (request.floor == _currentFloor) && _floorStops.isEmpty => ElevatorState.IDLE
      case None | _ => ElevatorState.IDLE
    }

  override def moveToNextFloor(): Unit = {
    _currentFloor += (
      determineElevatorState match
        case ElevatorState.UP => 1
        case ElevatorState.DOWN => -1
        case _ => 0
      )
  }

}

object Elevator {

  def apply(id: Int, outsideUpRequests: TPriorityQueue[OutsideUpRequest],
            outsideDownRequests: TPriorityQueue[OutsideDownRequest],
            insideRequests: TPriorityQueue[InsideRequest]): ElevatorImpl =
    ElevatorImpl(id, outsideUpRequests, outsideDownRequests, insideRequests)
}