package zio2.elevator

import zio.stm.TPriorityQueue

import java.time.{Duration, Instant}

sealed trait Request:
  def floor: Int

  def creationTime: Instant

  def elevatorTripData: ElevatorTripData

  def withPickedByStatistics(elevator: Elevator): Request

  def withDroppedOffAtStatistics(floor: Int): Request

//////////////////////////////////////////////////////////////////////////////////////////////////

case class InsideElevatorRequest(floor: Int,
                                 creationTime: Instant = Instant.now,
                                 elevatorTripData: ElevatorTripData = ElevatorTripData()) extends Request {

  override def withPickedByStatistics(elevator: Elevator): InsideElevatorRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )
  override def withDroppedOffAtStatistics(floor: Int): InsideElevatorRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      droppedOffAt = Some(Instant.now),
      droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(🛗:$floor;stats:[$elevatorTripData])"

}

//////////////////////////////////////////////////////////////////////////////////////////////////

case class OutsideUpRequest(floor: Int,
                            creationTime: Instant = Instant.now,
                            elevatorTripData: ElevatorTripData = ElevatorTripData()) extends Request {
  override def withPickedByStatistics(elevator: Elevator): OutsideUpRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )

  override def withDroppedOffAtStatistics(floor: Int): OutsideUpRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      droppedOffAt = Some(Instant.now),
      droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(⬆️:$floor;stats:[$elevatorTripData])"
}

//////////////////////////////////////////////////////////////////////////////////////////////////

case class OutsideDownRequest(floor: Int,
                              creationTime: Instant = Instant.now,
                              elevatorTripData: ElevatorTripData = ElevatorTripData()) extends Request {
  override def withPickedByStatistics(elevator: Elevator): OutsideDownRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )
 
  override def withDroppedOffAtStatistics(floor: Int): OutsideDownRequest =
    this.copy(elevatorTripData = this.elevatorTripData.copy(
      droppedOffAt = Some(Instant.now),
      droppedOffAtFloor = Some(floor))
    )

  override def toString: String = s"(⬇️:$floor;stats:[$elevatorTripData])"

}

//////////////////////////////////////////////////////////////////////////////////////////////////

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) => {
    (x, y) match {
      case (x: InsideElevatorRequest, y: InsideElevatorRequest) => x.floor.compareTo(y.floor) // Order InsideRequests by floor
      case (_: InsideElevatorRequest, _: OutsideDownRequest) => 1
      case (_: OutsideDownRequest, _: InsideElevatorRequest) => -1
      case (_: InsideElevatorRequest, _: OutsideUpRequest) => 1
      case (_: OutsideUpRequest, _: InsideElevatorRequest) => -1
      case (x, y) => x.creationTime.compareTo(y.creationTime) // Otherwise order by time, earlier to recent
    }
  }

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) => Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) => Request.requestOrdering.compare(x, y)

  implicit val insideRequestDescendingFloorOrder: Ordering[InsideElevatorRequest] =
    (x: InsideElevatorRequest, y: InsideElevatorRequest) => -1 * Request.requestOrdering.compare(x, y)

  def makeQueue[B <: Request : Ordering](initial: B*) = {
    TPriorityQueue.empty[B].flatMap { queue =>
      queue.offerAll(initial).as(queue)
    }.commit
  }

}