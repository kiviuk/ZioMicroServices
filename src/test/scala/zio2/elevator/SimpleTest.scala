package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Chunk, Queue, Task, ZIO, Ref}
import zio.test.{Spec, ZIOSpecDefault, assertTrue}
import Request.makeQueue
import java.time.Instant

case class AddOne1(ref: Ref[Vector[Double]]) {

  def add1 =
    ref.getAndUpdate(vector => vector :+ 1d)

  def get = ref.get

}

object SimpleTest extends ZIOSpecDefault {

  def spec = suite("ElevatorTripStatsCollector") {

    test("ElevatorTripStats should correctly compute trip time in ms") {

      for {
        vecRef <- Ref.make(Vector[Double]()).map(ref => new AddOne1(ref))
        _ <- vecRef.add1
        x <- vecRef.get

      } yield assertTrue(x.size == 1)

    }
  }
}
