package zio2.elevator

import zio.stm.TPriorityQueue

import java.time.{Duration, Instant}
import zio.ZIO
import zio.stm.ZSTM

sealed trait Request:
  def floor: Int

  def creationTime: Instant

  def tripData: ElevatorTripData

  def withPickedByInfo(elevator: Elevator): Request

  def withDroppedOffAtInfo(floor: Int): Request

////////////////////////////////////////////////////////////////////////////////

case class InsideElevatorRequest(
    floor: Int,
    creationTime: Instant = Instant.now,
    tripData: ElevatorTripData = ElevatorTripData()
) extends Request {

  override def withPickedByInfo(
      elevator: Elevator
  ): InsideElevatorRequest =
    this.copy(tripData =
      this.tripData.copy(
        pickedUpAt = Some(Instant.now),
        servedByElevator = Some(elevator.id),
        pickedUpOnFloor = Some(elevator.currentFloor)
      )
    )
  override def withDroppedOffAtInfo(floor: Int): InsideElevatorRequest =
    this.copy(tripData =
      this.tripData
        .copy(droppedOffAt = Some(Instant.now), droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(🛗: $floor;stats:[$tripData])"

}

////////////////////////////////////////////////////////////////////////////////

case class OutsideUpRequest(
    floor: Int,
    creationTime: Instant = Instant.now,
    tripData: ElevatorTripData = ElevatorTripData()
) extends Request {
  override def withPickedByInfo(elevator: Elevator): OutsideUpRequest =
    this.copy(tripData =
      this.tripData.copy(
        pickedUpAt = Some(Instant.now),
        servedByElevator = Some(elevator.id),
        pickedUpOnFloor = Some(elevator.currentFloor)
      )
    )

  override def withDroppedOffAtInfo(floor: Int): OutsideUpRequest =
    this.copy(tripData =
      this.tripData
        .copy(droppedOffAt = Some(Instant.now), droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(⬆️: $floor;stats:[$tripData])"
}

////////////////////////////////////////////////////////////////////////////////

case class OutsideDownRequest(
    floor: Int,
    creationTime: Instant = Instant.now,
    tripData: ElevatorTripData = ElevatorTripData()
) extends Request {
  override def withPickedByInfo(elevator: Elevator): OutsideDownRequest =
    this.copy(tripData =
      this.tripData.copy(
        pickedUpAt = Some(Instant.now),
        servedByElevator = Some(elevator.id),
        pickedUpOnFloor = Some(elevator.currentFloor)
      )
    )

  override def withDroppedOffAtInfo(floor: Int): OutsideDownRequest =
    this.copy(tripData =
      this.tripData
        .copy(droppedOffAt = Some(Instant.now), droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(⬇️: $floor;stats:[$tripData])"

}

////////////////////////////////////////////////////////////////////////////////

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) =>
    {
      (x, y) match {

        case (x: InsideElevatorRequest, y: InsideElevatorRequest) =>
          x.floor.compareTo(y.floor) // Order InsideRequests by floor

        case (_: InsideElevatorRequest, _: OutsideDownRequest) => 1
        case (_: OutsideDownRequest, _: InsideElevatorRequest) => -1
        case (_: InsideElevatorRequest, _: OutsideUpRequest)   => 1
        case (_: OutsideUpRequest, _: InsideElevatorRequest)   => -1

        // Otherwise order by time, earlier to recent
        case (x, y) => x.creationTime.compareTo(y.creationTime)
      }
    }

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) =>
      Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) =>
      Request.requestOrdering.compare(x, y)

  implicit val insideRequestDescendingFloorOrder
      : Ordering[InsideElevatorRequest] =
    (x: InsideElevatorRequest, y: InsideElevatorRequest) =>
      -1 * Request.requestOrdering.compare(x, y)

  def makeChannel[B <: Request: Ordering](initial: B*) = {
    TPriorityQueue
      .empty[B]
      .flatMap { queue =>
        queue.offerAll(initial).as(queue)
      }
      .commit
  }

  def emptyChannel[B <: Request: Ordering]
      : ZIO[Any, Nothing, TPriorityQueue[B]] = {
    TPriorityQueue.make[B]().commit
  }

}
