package zio2.elevator

import zio.stm.TPriorityQueue

import java.time.{Duration, Instant}

sealed trait Request:
  def floor: Int

  def creationTime: Instant

  def stats: RequestStatistics

  def withPickedByStatistics(elevator: Elevator): Request

  def withDequeuedAt(floor: Int): Request

//////////////////////////////////////////////////////////////////////////////////////////////////

case class InsideElevatorRequest(floor: Int,
                                 creationTime: Instant = Instant.now,
                                 stats: RequestStatistics) extends Request {

  override def withPickedByStatistics(elevator: Elevator): InsideElevatorRequest =
    this.copy(stats = this.stats.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )
  override def withDequeuedAt(floor: Int): InsideElevatorRequest =
    this.copy(stats = this.stats.copy(
      dequeuedAt = Some(Instant.now),
      destinationFloor = Some(floor))
    )

  override def toString: String = s"(🛗: $floor; ${Instant.now.minusMillis(creationTime.toEpochMilli())}; $stats)"
}

//////////////////////////////////////////////////////////////////////////////////////////////////

case class OutsideUpRequest(floor: Int,
                            creationTime: Instant = Instant.now,
                            stats: RequestStatistics) extends Request {
  override def withPickedByStatistics(elevator: Elevator): OutsideUpRequest =
    this.copy(stats = this.stats.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )

  override def withDequeuedAt(floor: Int): OutsideUpRequest =
    this.copy(stats = this.stats.copy(
      dequeuedAt = Some(Instant.now),
      destinationFloor = Some(floor))
    )

  override def toString: String = s"(⬆️: $floor, stats: $stats)"
}

//////////////////////////////////////////////////////////////////////////////////////////////////

case class OutsideDownRequest(floor: Int,
                              creationTime: Instant = Instant.now,
                              stats: RequestStatistics) extends Request {
  override def withPickedByStatistics(elevator: Elevator): OutsideDownRequest =
    this.copy(stats = this.stats.copy(
      pickedUpAt = Some(Instant.now),
      servedByElevator = Some(elevator.id),
      pickedUpOnFloor = Some(elevator.currentFloor))
    )
 
  override def withDequeuedAt(floor: Int): OutsideDownRequest =
    this.copy(stats = this.stats.copy(
      dequeuedAt = Some(Instant.now),
      destinationFloor = Some(floor))
    )

  override def toString: String = s"(⬇️: $floor, stats: $stats)"
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