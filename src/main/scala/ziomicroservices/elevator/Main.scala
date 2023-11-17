package ziomicroservices.elevator

import zio.*
import zio.stm.{STM, TPriorityQueue}

import java.time.Instant
import scala.collection.mutable

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

enum ElevatorState(val abbreviation: String):
  case UP extends ElevatorState("U")
  case DOWN extends ElevatorState("D")
  case IDLE extends ElevatorState("I")
  case HALT extends ElevatorState("H")

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

sealed trait Request:
  def floor: Int

  def time: Instant

case class InsideRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideUpRequest(floor: Int, time: Instant = Instant.now) extends Request

case class OutsideDownRequest(floor: Int, time: Instant = Instant.now) extends Request

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Request {
  implicit val requestOrdering: Ordering[Request] = (x: Request, y: Request) => {
    (x, y) match {
      case (x: InsideRequest, y: InsideRequest) => x.floor.compareTo(y.floor) // Order InsideRequests by floor
      case (_: InsideRequest, y: OutsideDownRequest) => 1
      case (x: OutsideDownRequest, _: InsideRequest) => -1
      case (_: InsideRequest, y: OutsideUpRequest) => -1
      case (x: OutsideUpRequest, _: InsideRequest) => 1
      case (x, y) if x.time.equals(y.time) => 0 // If same time, they are equal
      case (x, y) => x.time.compareTo(y.time) // Otherwise order by time, earlier to recent
    }
  }
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def determineElevatorState(_floorStops: mutable.SortedSet[Request], currentFloor: Int): ElevatorState =
  _floorStops.headOption match {
    case Some(request) if request.floor > currentFloor => ElevatorState.UP
    case Some(request) if request.floor < currentFloor => ElevatorState.DOWN
    case Some(request) if (request.floor == currentFloor) && _floorStops.nonEmpty => ElevatorState.HALT
    case Some(request) if (request.floor == currentFloor) && _floorStops.isEmpty => ElevatorState.IDLE
    case None | _ => ElevatorState.IDLE
  }

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

trait ElevatorCar {
  def id: String

  def upRequests: TPriorityQueue[OutsideUpRequest]

  def downRequests: TPriorityQueue[OutsideDownRequest]

  def insideRequests: TPriorityQueue[InsideRequest]

  def floorStops: mutable.SortedSet[Request]

  def currentFloor: Int

  def isAtFloorStop: Boolean

  def dequeueCurrentFloorStop(): Unit

  def nextFloor(): Unit

  def addFloorStop(request: Request): Unit

}

case class ElevatorCarImpl(_id: String,
                           _outsideUpRequests: TPriorityQueue[OutsideUpRequest],
                           _outsideDownRequests: TPriorityQueue[OutsideDownRequest],
                           _insideRequests: TPriorityQueue[InsideRequest],
                           _floorStops: mutable.SortedSet[Request] = mutable.SortedSet()) extends ElevatorCar {

  private var _currentFloor: Int = 0

  override def id: String = _id

  override def upRequests: TPriorityQueue[OutsideUpRequest] = _outsideUpRequests

  override def downRequests: TPriorityQueue[OutsideDownRequest] = _outsideDownRequests

  override def insideRequests: TPriorityQueue[InsideRequest] = _insideRequests

  override def floorStops: mutable.SortedSet[Request] = _floorStops

  override def currentFloor: Int = _currentFloor

  override def addFloorStop(request: Request): Unit = _floorStops.add(request)

  override def isAtFloorStop: Boolean = _floorStops.headOption match {
    case Some(nextStop: Request) => _currentFloor == nextStop.floor
    case _ => false
  }

  override def dequeueCurrentFloorStop(): Unit = _floorStops -= _floorStops.head

  override def nextFloor(): Unit = {
    _currentFloor += (
      determineElevatorState(_floorStops, _currentFloor) match
        case ElevatorState.UP => 1
        case ElevatorState.DOWN => -1
        case _ => 0
      )
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def acceptRequestConditionally[B <: Request](incomingRequests: TPriorityQueue[B],
                                             floorStops: mutable.SortedSet[Request],
                                             currentFloor: Int,
                                            ): IO[Nothing, Option[B]] = {

  def canElevatorAcceptRequest(requested: Option[B]): Boolean = {
    val elevatorState = determineElevatorState(floorStops, currentFloor)
    requested match {
      case Some(requested: B) =>
        elevatorState match
          case ElevatorState.IDLE => true // accept any request while elevator is idling
          case ElevatorState.UP => requested.floor > currentFloor // accept up requests only
          case ElevatorState.DOWN => requested.floor < currentFloor // accept down requests only
          case _ => false
      case _ => false
    }
  }

  {
    incomingRequests.peekOption.flatMap {
      mayBeRequest => {
        if (canElevatorAcceptRequest(mayBeRequest)) {
          incomingRequests.take.flatMap(_ => STM.succeed(mayBeRequest))
        } else {
          STM.succeed(None)
        }
      }
    }
  }.commit

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(elevatorCar: ElevatorCar, periodicity: Int) = {
  (for {

    _ <- Console.printLine(
      s"{Elevator ${elevatorCar.id}:" +
        s""" current floorRoute: "${elevatorCar.floorStops.toList.mkString(",")}",""" +
        s""" current floor "${elevatorCar.currentFloor}": checking incoming queue"""
    ).orDie

    _ <- acceptRequestConditionally(
      elevatorCar.insideRequests,
      elevatorCar.floorStops,
      elevatorCar.currentFloor,
      "insideRequests") flatMap {
      case Some(insideRequest: InsideRequest) =>
        ZIO.succeed(elevatorCar.addFloorStop(insideRequest))
      case _ =>
        ZIO.unit
    }

    _ <- acceptRequestConditionally(
      elevatorCar.upRequests,
      elevatorCar.floorStops,
      elevatorCar.currentFloor,
      "upRequests") flatMap {
      case Some(upwardRequest: OutsideUpRequest) =>
        ZIO.succeed(elevatorCar.addFloorStop(upwardRequest))
      case _ =>
        ZIO.unit
    }

    _ <- acceptRequestConditionally(
      elevatorCar.downRequests,
      elevatorCar.floorStops,
      elevatorCar.currentFloor,
      "downRequests") flatMap {
      case Some(outsideDownRequest: OutsideDownRequest) => {
        println(s"Accepting downRequest $outsideDownRequest")
        ZIO.succeed(elevatorCar.addFloorStop(outsideDownRequest))
      }
      case _ =>
        ZIO.unit
    }

    _ <- ZIO.succeed {
      if (elevatorCar.isAtFloorStop) {
        println(s"{Elevator ${elevatorCar.id}: reached floor ${elevatorCar.currentFloor}}")
        elevatorCar.dequeueCurrentFloorStop()
      }
    }

    _ <- ZIO.succeed(elevatorCar.nextFloor())

  } yield ()).repeat(Schedule.spaced(Duration.fromSeconds(periodicity)))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////
object Main extends ZIOAppDefault {

  implicit val outsideUpRequestOrdering: Ordering[OutsideUpRequest] =
    (x: OutsideUpRequest, y: OutsideUpRequest) => Request.requestOrdering.compare(x, y)

  implicit val outsideDownRequestOrdering: Ordering[OutsideDownRequest] =
    (x: OutsideDownRequest, y: OutsideDownRequest) => Request.requestOrdering.compare(x, y)

  implicit val insideRequestOrdering: Ordering[InsideRequest] =
    (x: InsideRequest, y: InsideRequest) => Request.requestOrdering.compare(x, y)

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private object ElevatorCar {
    def apply(id: String, outsideUpRequests: TPriorityQueue[OutsideUpRequest],
              outsideDownRequests: TPriorityQueue[OutsideDownRequest],
              insideRequests: TPriorityQueue[InsideRequest]): ElevatorCarImpl =
      ElevatorCarImpl(id, outsideUpRequests, outsideDownRequests, insideRequests)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private def program = {

    for {

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- TPriorityQueue.make[OutsideUpRequest](OutsideUpRequest(14)).commit
      insidePassengerRequestQueueElevator1 <- TPriorityQueue.make[InsideRequest](InsideRequest(3)).commit

      // Creating outsideDownRequestQueue and scheduling an OutsideDownRequest to be added in 10 seconds
      outsideDownRequestQueue <- TPriorityQueue.make[OutsideDownRequest]().commit
      _ <- outsideDownRequestQueue.offer(OutsideDownRequest(1)).commit.delay(Duration.fromSeconds(10)).fork

      // elevator #1
      elevator1 <- ZIO.succeed(ElevatorCar("1",
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        insidePassengerRequestQueueElevator1))

      _ <- simulate(elevator1, 1).fork

      _ <- Console.readLine("Press any key to exit...\n")

    } yield ()
  }

  def run = program

}