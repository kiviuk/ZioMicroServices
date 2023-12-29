package zio2.elevator
import zio.stm.TPriorityQueue
import zio.{Chunk, Queue, Task, ZIO}
import zio.test.{Spec, ZIOSpecDefault, assertTrue}
import java.time.Instant
import zio.test.Assertion.{contains, equalTo, hasSize, exists}
import scala.collection.mutable
import zio2.elevator.Request.makeChannel

object RequestTest extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("Request")(
    test("Should create request") {
      val r = InsideElevatorRequest(1, tripData = ElevatorTripData())
      assertTrue(r.creationTime.isBefore(Instant.now().plusMillis(1000)))
    },
    test("Should order requests by descending floor number") {

      // arrange
      val topFloor = InsideElevatorRequest(100, tripData = ElevatorTripData())
      val bottomFloor = InsideElevatorRequest(1, tripData = ElevatorTripData())

      // act
      import Request.insideRequestDescendingFloorOrder
      val vec: Vector[InsideElevatorRequest] =
        Vector(bottomFloor, topFloor).sorted

      // assert
      assertTrue(vec(0).equals(topFloor))
      assertTrue(vec(1).equals(bottomFloor))

    },
    test("Should order requests by descending floor number2") {

      // arrange
      val bottomFloor = InsideElevatorRequest(-100, tripData = ElevatorTripData())
      val topFloor = InsideElevatorRequest(1, tripData = ElevatorTripData())

      // act
      import Request.insideRequestDescendingFloorOrder
      val vec: Vector[InsideElevatorRequest] =
        Vector(bottomFloor, topFloor).sorted

      // assert
      assertTrue(vec(0).equals(topFloor))
      assertTrue(vec(1).equals(bottomFloor))

    },
    test("Should queue up inside elevator requests by descending floor number") {

      // arrange
      val bottomFloor = InsideElevatorRequest(-100, tripData = ElevatorTripData())
      val topFloor = InsideElevatorRequest(1, tripData = ElevatorTripData())

      // act
      val queue = makeChannel(bottomFloor, topFloor)

      // assert
      for {
        q <- queue
        twoRequests <- q.take.flatMap(h => q.take.map(t => (h, t))).commit
      } yield (assertTrue(
        twoRequests._1.equals(topFloor),
        twoRequests._2.equals(bottomFloor)
      ))

    }
  )
}
