package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Chunk, Queue, Task, ZIO, Ref}
import zio.test.{Spec, ZIOSpecDefault, assertTrue}
import Request.makeQueue
import java.time.Instant

object ElevatorTripDataCollectorTest extends ZIOSpecDefault {

  def spec = suite("ElevatorTripDataCollector") {

    test("ElevatorTripStats should correctly compute trip time in ms") {
      val time = Instant.now

      val trip = new ElevatorTripData(
        Some("elevator-id-1"),
        createdAt = time,
        pickedUpAt = Some(time.plusMillis(10)),
        droppedOffAt = Some(time.plusMillis(15)),
        pickedUpOnFloor = Some(1),
        droppedOffAtFloor = Some(10)
      )

      assertTrue(trip.getTripTimeInMillis == Some(5.0))
    }

    test(
      "ElevatorTripDataCollector should report number of elevator trips"
    ) {

      val time = Instant.now

      val trip1 = new ElevatorTripData(
        Some("elevator-id-1"),
        createdAt = time,
        pickedUpAt = Some(time.plusMillis(10)),
        droppedOffAt = Some(time.plusMillis(15)),
        pickedUpOnFloor = Some(1),
        droppedOffAtFloor = Some(10)
      )

      val trip2 = new ElevatorTripData(
        Some("elevator-id-2"),
        createdAt = time,
        pickedUpAt = Some(time.plusMillis(10)),
        droppedOffAt = Some(time.plusMillis(15)),
        pickedUpOnFloor = Some(1),
        droppedOffAtFloor = Some(10)
      )

      val trip3 = new ElevatorTripData(
        Some("elevator-id-3"),
        createdAt = time,
        pickedUpAt = Some(time.plusMillis(10)),
        droppedOffAt = Some(time.plusMillis(15)),
        pickedUpOnFloor = Some(1),
        droppedOffAtFloor = Some(10)
      )
      
      val trip4 = new ElevatorTripData(
        Some("elevator-id-4"),
        createdAt = time,
        pickedUpAt = Some(time.plusMillis(10)),
        droppedOffAt = Some(time.plusMillis(15)),
        pickedUpOnFloor = Some(1),
        droppedOffAtFloor = Some(10)
      )

      for {

        tripDataCollector <- Ref
          .make(Vector[ElevatorTripData]())
          .map(storage => ElevatorTripDataCollector(storage))

        _ <- tripDataCollector.add(trip1)
        _ <- tripDataCollector.add(trip2)
        _ <- tripDataCollector.addAll(Seq(trip3, trip4))

        totalTrips <- tripDataCollector.numberOfTrips
        totalTripTime <- tripDataCollector.tripTimeTotalInMillis
        totalTripTime2 <- tripDataCollector.tripTimeTotalInMillis

      } yield assertTrue(totalTrips == 4, totalTripTime == 4 * 5.0, totalTripTime == totalTripTime2)
    }
  }
}
