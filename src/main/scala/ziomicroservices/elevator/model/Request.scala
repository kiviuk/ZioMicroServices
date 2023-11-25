package ziomicroservices.elevator.model
import java.time.Instant

sealed trait Request:
  def floor: Int

  def time: Instant

case class InsideElevatorRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideUpRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideDownRequest(floor: Int, time: Instant = Instant.now) extends Request

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) => {
    (x, y) match {
      case (x: InsideElevatorRequest, y: InsideElevatorRequest) => x.floor.compareTo(y.floor) // Order InsideRequests by floor
      case (_: InsideElevatorRequest, y: OutsideDownRequest) => 1
      case (x: OutsideDownRequest, _: InsideElevatorRequest) => -1
      case (_: InsideElevatorRequest, y: OutsideUpRequest) => 1
      case (x: OutsideUpRequest, _: InsideElevatorRequest) => -1
      case (x, y) if x.time.equals(y.time) => 0 // If same time, they are equal
      case (x, y) => x.time.compareTo(y.time) // Otherwise order by time, earlier to recent
    }
  }
}