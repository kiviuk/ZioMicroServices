package zio2.elevator

import zio.*
import zio.stm.{STM, TPriorityQueue}
import zio2.elevator
import Request.*

import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import scala.collection.mutable

// TODO: Collect runtime statistics based on travel time, travel distance, averages, maximums, minimums...
//       - Every request created by the elevator system is
//         timestamped at creation, yielding the 'creation-time' [C] - a point in time.
//       - When the elevator picks-up a request the pick-up [P] time is recorded.
//       - Once the elevator reaches the requested floor the fulfilled-time [F] is recorded
//       - the total-time DT        : [F] - [C]
//       - the fulfillment-time DF  : [F] - [P]
//       - the waiting-time DW      : [P] - [C]
//       In short:
//       creation-time, pick-up-time, fulfilled-time are points in time
//       total-time, fulfillment-time, waiting-time are durations
//       - Log the number of floors covered, for ever fulfilled request
//       - Log every reached floor
//
//       1 logfile per elevator
//

// TODO: Problem: The elevator is constantly moving and needs to decide between multiple requests.
//       To solve this, a nearest next floor algorithm can be used.
//       However, a priority queue is needed that can re-prioritize depending on the elevator's current floor.
//       How about a self-reorganizing data structure for elevators? When the elevator is already heading up,
//       the next nearest floor destination becomes the head of the data structure, always adjusted for the
//       current elevator floor position. Every time the elevator reaches a floor,
//       that floor is removed from the data structure.
//       research: https://www.youtube.com/watch?v=6JxvKfSV9Ns
//       JGraphTs,  org.jgrapht.util
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

def simulate(elevator: Elevator, periodicity: Int) = {

  def conditionallyAcceptRequest[B <: Request](requestQueue: TPriorityQueue[B],
                                               canElevatorAccept: Option[B] => Boolean
                                              ): ZIO[Any, Nothing, Option[B]] = {
    val requestCheck = for {
      maybeRequest <- requestQueue.peekOption
      result <- if (canElevatorAccept(maybeRequest)) requestQueue.take.as(maybeRequest) else STM.succeed(None)
    } yield result
    requestCheck.commit
  }

  def acceptRequest[B <: Request](requested: Option[B]): Boolean = {
    requested.isDefined
  }

  def canElevatorAcceptRequest[B <: Request](maybeRequest: Option[B]): Boolean = maybeRequest.exists {
    request =>
      elevator.determineElevatorState match {
        case ElevatorState.IDLE => true
        case ElevatorState.HEADING_UP => request.floor > elevator.currentFloor
        case ElevatorState.HEADING_DOWN => request.floor < elevator.currentFloor
        case _ => false
      }
  }

  def handleRequestBasedOnElevatorState[B <: Request]: TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, canElevatorAcceptRequest)

  def alwaysAcceptInsideRequest[B <: Request]: TPriorityQueue[B] => ZIO[Any, Nothing, Option[B]] =
    conditionallyAcceptRequest(_, acceptRequest)

  val map1: Map[String, String] = Map("🚽" -> GREEN, "2" -> BLUE, "3" -> YELLOW)

  val idColor = map1(elevator.id)

  val x = elevator.floorStops

  def y = if (x.nonEmpty)
    s"""floorRoute: "${x.mkString(", ")}", """
  else
    ""

  (for {

    _ <- Console.printLine(
      s"${idColor}{Elevator ${elevator.id}: $y" +
        s"""🏠 "${elevator.currentFloor}":D:${elevator.isHeadingDown}:U:${elevator.isHeadingUp} checking incoming queue${RESET}"""
    )

    _ <- alwaysAcceptInsideRequest(elevator.insideRequests) flatMap {
      case Some(insideRequest: InsideElevatorRequest) if !elevator.floorStops.contains(insideRequest) =>
        ZIO.succeed(elevator.addFloorStop(insideRequest))
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.upRequests) flatMap {
      case Some(outsideUpRequest: OutsideUpRequest) =>
        ZIO.succeed(elevator.addFloorStop(outsideUpRequest))
      case _ =>
        ZIO.unit
    }

    _ <- handleRequestBasedOnElevatorState(elevator.downRequests) flatMap {
      case Some(outsideDownRequest: OutsideDownRequest) =>
        ZIO.succeed(elevator.addFloorStop(outsideDownRequest))
      case _ =>
        ZIO.unit
    }

    _ <- ZIO.succeed {
      val reachedStops = elevator.floorStops.filter(_.floor == elevator.currentFloor)
      elevator.floorStops --= reachedStops
      if (reachedStops.nonEmpty)
        println(s"""${idColor}{Elevator ${elevator.id}:${RESET} ${RED}reached floor ${elevator.currentFloor}}${RESET}""")
        println(s"""${idColor}{Elevator ${elevator.id}:${RESET} ${RED}Removing ${reachedStops}${RESET}""")
    }

    _ <- ZIO.succeed(elevator.moveToNextFloor())

  } yield ()).repeat(Schedule.spaced(Duration.fromMillis(periodicity)))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

object Main extends ZIOAppDefault {

  import Request._

  private def program = {

    for {

      // Creating initial request queues. Adding an OutsideUpRequest to the outsideUpRequestQueue
      // and an InsideRequest to the insidePassengerRequestQueueElevator1
      outsideUpRequestQueue <- makeQueue[OutsideUpRequest](OutsideUpRequest(14))
      outsideDownRequestQueue <- makeQueue[OutsideDownRequest]()

      insidePassengerRequestQueueElevator1 <- makeQueue(InsideElevatorRequest(3), InsideElevatorRequest(-1))
      insidePassengerRequestQueueElevator2 <- makeQueue(InsideElevatorRequest(5))
      insidePassengerRequestQueueElevator3 <- makeQueue(InsideElevatorRequest(5))

      // Creating outsideDownRequestQueue and scheduling an OutsideDownRequest to be added in 10 seconds
      _ <- outsideDownRequestQueue.offer(OutsideDownRequest(1)).commit.delay(Duration.fromSeconds(10)).fork

      // elevator #1
      elevator1 <- ZIO.succeed(elevator.Elevator("🚽",
        outsideUpRequestQueue,
        outsideDownRequestQueue,
        insidePassengerRequestQueueElevator1))

      // elevator #2
      elevator2 <- ZIO.succeed(elevator.Elevator("2",
        outsideUpRequestQueue,
        outsideDownRequestQueue,

        insidePassengerRequestQueueElevator2))

      // elevator #3
      elevator3 <- ZIO.succeed(elevator.Elevator("3",
        outsideUpRequestQueue,
        outsideDownRequestQueue,

        insidePassengerRequestQueueElevator3))

      _ <- ZIO.foreachParDiscard(List(elevator1, elevator2, elevator3))(simulate(_, 10).fork)

      _ <- ElevatorRequestHandler.start(
        List(elevator1.insideRequests, elevator2.insideRequests, elevator3.insideRequests),
        outsideUpRequestQueue,
        outsideDownRequestQueue).either race Console.readLine("Press any key to exit...\n")

    } yield ()
  }

  def run = program

}