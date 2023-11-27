package zio2.elevator.model
import zio.stm.TPriorityQueue

import java.time.{Duration, Instant}

sealed trait Request:
  def floor: Int

  def time: Instant

private def getTimePassedInSeconds(time: Instant): Long = Duration.between(time, Instant.now).getSeconds

case class InsideElevatorRequest(floor: Int, time: Instant = Instant.now) extends Request {
  override def toString: String = s"(🛗: $floor, sec ago: ${getTimePassedInSeconds(time)})"
}

case class OutsideUpRequest(floor: Int, time: Instant = Instant.now) extends Request {
  override def toString: String = s"(⬆️: $floor, sec ago: ${getTimePassedInSeconds(time)})"
}

case class OutsideDownRequest(floor: Int, time: Instant = Instant.now) extends Request {
  override def toString: String = s"(⬇️: $floor, sec ago: ${getTimePassedInSeconds(time)})"
}

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) => {
    (x, y) match {
      case (x: InsideElevatorRequest, y: InsideElevatorRequest) => x.floor.compareTo(y.floor) // Order InsideRequests by floor
      case (_: InsideElevatorRequest, _: OutsideDownRequest) => 1
      case (_: OutsideDownRequest, _: InsideElevatorRequest) => -1
      case (_: InsideElevatorRequest, _: OutsideUpRequest) => 1
      case (_: OutsideUpRequest, _: InsideElevatorRequest) => -1
      case (x, y) => x.time.compareTo(y.time) // Otherwise order by time, earlier to recent
    }
  }

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) => Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) => Request.requestOrdering.compare(x, y)

  implicit val insideRequestOrdering: Ordering[InsideElevatorRequest] =
    (x: InsideElevatorRequest, y: InsideElevatorRequest) => -1 * Request.requestOrdering.compare(x, y)

  def makeQueue[B <: Request : Ordering](initial: B*) = {
    TPriorityQueue.empty[B].flatMap { queue =>
      queue.offerAll(initial).as(queue)
    }.commit
  }
}