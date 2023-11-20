package ziomicroservices.elevator.model
import java.time.Instant

sealed trait Request:
  def floor: Int

  def time: Instant

case class InsideRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideUpRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideDownRequest(floor: Int, time: Instant = Instant.now) extends Request

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) => {
    (x, y) match {
      case (x: InsideRequest, y: InsideRequest) => x.floor.compareTo(y.floor) // Order InsideRequests by floor
      case (_: InsideRequest, y: OutsideDownRequest) => 1
      case (x: OutsideDownRequest, _: InsideRequest) => -1
      case (_: InsideRequest, y: OutsideUpRequest) => 1
      case (x: OutsideUpRequest, _: InsideRequest) => -1
      case (x, y) if x.time.equals(y.time) => 0 // If same time, they are equal
      case (x, y) => x.time.compareTo(y.time) // Otherwise order by time, earlier to recent
    }
  }
}