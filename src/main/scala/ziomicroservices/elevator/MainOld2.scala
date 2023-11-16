//package ziomicroservices.elevator
//
//import zio.*
//import ziomicroservices.elevator.service.elevator.Direction
//
//import scala.collection.mutable
//
//object Main extends ZIOAppDefault {
//
//  trait ElevatorCar {
//    def currentFloor: Int
//
//    def direction: Int // UP: pos, DOWN: neg., IDLE: zero
//
//    def destinationFloor: Int
//
//    def updateCurrentFloor(floor: Int): Unit
//
//    def addDestinationFloor(floor: Int): Unit
//
//    def incomingPassengerRequests: Queue[Int]
//
//    def dequeueFromFloorRoute(floor: Int): Unit
//
//    def hasFloor(floor: Int): Boolean
//
//    def hasNoFloorRoute: Boolean
//
//    def showFloorRoute: mutable.SortedSet[Int]
//
//    def updateDirection(direction: Int): Unit
//
//    def id: String
//  }
//
//  case class ElevatorCarImpl(_id: String,
//                             _incomingPassengerRequestsUp: Queue[Int],
//                             _incomingPassengerRequestsDown: Queue[Int],
//                             _floorRoute: mutable.SortedSet[Int] = mutable.SortedSet()) extends ElevatorCar {
//    private var _currentFloor: Int = 0
//
//    private var _direction: Int = 0 // UP: pos, DOWN: neg., IDLE: zero
//
//    override def currentFloor: Int = _currentFloor
//
//    override def direction: Int = _direction
//
//    override def updateCurrentFloor(floor: Int): Unit = _currentFloor = floor
//
//    override def incomingPassengerRequests: Queue[Int] = _incomingPassengerRequestsUp
//
//    override def destinationFloor: Int = if _floorRoute.isEmpty then 0 else _floorRoute.last
//
//    override def addDestinationFloor(floor: Int): Unit = _floorRoute.add(floor)
//
//    override def dequeueFromFloorRoute(floor: Int): Unit = _floorRoute.remove(floor)
//
//    override def hasFloor(floor: Int): Boolean = _floorRoute.contains(floor)
//
//    override def hasNoFloorRoute: Boolean = _floorRoute.isEmpty
//
//    override def showFloorRoute: mutable.SortedSet[Int] = _floorRoute
//
//    override def updateDirection(direction: Int): Unit = _direction = direction
//
//    override def id: String = _id
//  }
//
//  def simulateElevator(elevatorCar: ElevatorCar, duration: Int): ZIO[Any, Nothing, Long] = {
//    (for {
//
//      _ <- Console.printLine(
//        s"{Elevator ${elevatorCar.id}:" +
//          s""" current floorRoute: "${elevatorCar.showFloorRoute.toList.mkString(",")}",""" +
//          s""" current floor "${elevatorCar.currentFloor}": checking incoming queue"""
//      ).orDie
//
//      _ <- elevatorCar.incomingPassengerRequests.poll.flatMap {
//        case Some(request) if elevatorCar.hasNoFloorRoute || request > elevatorCar.currentFloor =>
//          println(s"{Elevator ${elevatorCar.id}: current floor ${elevatorCar.currentFloor}, adding next floor stop: $request")
//          ZIO.succeed {
//            elevatorCar.addDestinationFloor(request)
//          }
//        case Some(request) =>
//          println(s"{Elevator ${elevatorCar.id}: current floor ${elevatorCar.currentFloor}, DENIED floor request for: $request")
//          elevatorCar.incomingPassengerRequests.offer(request)
//          ZIO.unit
//        case None if elevatorCar.hasNoFloorRoute =>
//          elevatorCar.incomingPassengerRequests.take.map(x => elevatorCar.addDestinationFloor(x))
//        case _ =>
//          ZIO.unit
//      }
//
//      _ <- ZIO.succeed {
//        if (elevatorCar.hasFloor(elevatorCar.currentFloor)) {
//          println(s"{Elevator ${elevatorCar.id}: reached floor ${elevatorCar.currentFloor}}")
//          elevatorCar.dequeueFromFloorRoute(elevatorCar.currentFloor)
//        }
//      }
//
//      _ <- ZIO.succeed(if (!elevatorCar.hasNoFloorRoute) {
//        elevatorCar.updateCurrentFloor(elevatorCar.currentFloor + 1)
//      })
//
//    } yield ()).repeat(Schedule.spaced(Duration.fromSeconds(duration)))
//  }
//
//  def dispatcher(requestQueueGlobal: Queue[Int],
//                 requestQueueUp: Queue[Int],
//                 requestQueueDown: Queue[Int],
//                ) = {
//
//    (for {
//      _ <- Console.printLine(s"{Dispatcher: checking queue}").orDie
//      request <- requestQueueUp.take
//      elevator1QueueSize <- elevator1Queue.size
//      //      elevator2QueueSize <- elevator2Queue.size
//      _ <- Console.printLine(s"{Dispatcher: found request for floor: $request}").orDie
//      _ <- elevator1Queue.offer(request)
//      //      _ <- if (elevator1QueueSize < elevator2QueueSize) {
//      //        Console.printLine("{Dispatcher: Elevator 2 busy!}").orDie
//      //      }
//      //      else {
//      //        Console.printLine("{Dispatcher: Elevator 1 busy!}").orDie
//      //      }
//      //      _ <- if (elevator1QueueSize < elevator2QueueSize) {
//      //        elevator1Queue.offer(request)
//      //      }
//      //      else {
//      //        elevator2Queue.offer(request)
//      //      }
//
//
//    } yield ()).repeat(Schedule.spaced(Duration.fromSeconds(5)))
//  }
//
//
//  def run = {
//
//    for {
//
//      requestQueueUp <- Queue.bounded[Int](40)
//      requestQueueDown <- Queue.bounded[Int](40)
//      requestQueueGlobal <- Queue.bounded[Int](40)
//
//      _ <- requestQueueGlobal.offerAll(List(1, 15, 19, 16, -1))
//
//
//      elevator1 <- ZIO.succeed(ElevatorCarImpl("1", requestQueueUp, requestQueueDown))
//      //      elevator2 <- ZIO.succeed(ElevatorCarImpl("2", elevator2Queue))
//
//      elevator1Fiber <- simulateElevator(elevator1, 1).fork
//      //      elevator2Fiber <- simulateElevator(elevator2, 1).fork
//
//      dispatchFiber <- dispatcher(requestQueueGlobal, requestQueueUp, requestQueueDown).fork
//
//      _ <- dispatchFiber.join
//
//    } yield ()
//  }
//
//  //  def run: ZIO[Any, Any, Unit] = {
//  //    (for {
//  //      system <- ZIO.service[SchoolElevatorSystem]
//  //      el <- system
//  //        .findElevator("0")
//  //        .option
//  //        .flatMap {
//  //          case Some(elevator) => ZIO.succeed("FOUND")
//  //          case None => ZIO.succeed("ERRR")
//  //        }
//  //    } yield ())
//  //      .provide(
//  //        SchoolElevatorSystem.layer
//  //      )
//  //  }
//}