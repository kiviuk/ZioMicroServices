package zio2.elevator

import zio.stm.TPriorityQueue
import zio2.elevator
import ElevatorState.IDLE

import scala.collection.mutable
import zio.nio.file.Files
import java.nio.file.StandardOpenOption
import zio.nio.charset.Charset
import zio.nio.file.Path
import zio.ZIO

trait Elevator:
  def id: String

  def insideChannel: TPriorityQueue[InsideElevatorRequest]

  def floorStops: mutable.SortedSet[Request]

  def currentFloor: Int

  def dequeueReachedFloorStop(reachedStop: Request): Unit

  def moveToFloor(floor: Int): Unit

  def addFloorStop(request: Request): Unit

case class ElevatorImpl(
  _id: String,
  _insideRequests: TPriorityQueue[InsideElevatorRequest],
  _scheduledStops: mutable.SortedSet[Request] = mutable.SortedSet()
) extends Elevator:

  private var _currentFloor: Int = 0

  def hasScheduledStop(targetFloor: Request): Boolean =
    _scheduledStops.exists(r => r.floor == targetFloor.floor)

  override def id: String = _id

  override def insideChannel: TPriorityQueue[InsideElevatorRequest] =
    _insideRequests

  override def floorStops: mutable.SortedSet[Request] = _scheduledStops

  override def currentFloor: Int = _currentFloor

  override def addFloorStop(request: Request): Unit =
    if !hasScheduledStop(request) then _scheduledStops.add(request)

  override def moveToFloor(floor: Int): Unit = _currentFloor = floor

  override def dequeueReachedFloorStop(
    reachedStop: Request
  ): Unit =
    _scheduledStops -= reachedStop

object Elevator:

  def apply(
    id: String,
    insideQueue: TPriorityQueue[InsideElevatorRequest]
  ): ElevatorImpl =
    ElevatorImpl(
      id,
      insideQueue
    )

  def logElevator(reachedStops: mutable.SortedSet[Request]) =
    Files
      .writeLines(
        path = Path("logs.txt"),
        lines = reachedStops.map(req => s"${req.elevatorTripData}").toList,
        charset = Charset.defaultCharset,
        openOptions = Set(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      )
      .catchAll(t => ZIO.succeed(println(t.getMessage)))
