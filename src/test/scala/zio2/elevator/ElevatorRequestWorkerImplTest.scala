package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Chunk, Queue, Task, ZIO}
import zio.test.Assertion.equalTo
import zio.test.{Spec, ZIOSpecDefault, assertTrue, assertZIO}
import zio2.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest}
import zio2.elevator.model.Request.*

object ElevatorRequestWorkerImplTest extends ZIOSpecDefault {

  def spec = suite("DecoderTest") {

    val cmdString = "m:1:4|r:1:u"

    val data: List[Either[Throwable, Chunk[Byte]]] = cmdString.map { char =>
      Right(Chunk.single(char.toByte))
    }.toList

    val testSocketService: ZIO[Any, Nothing, SocketService] = TestSocketService.create(data)

    test("Decoder should correctly decode valid commands") {

      for {
        insideElevator <- makeQueue[InsideElevatorRequest]()
        ou <- makeQueue[OutsideUpRequest]()
        od <- makeQueue[OutsideDownRequest]()
        socketService <- testSocketService
        requestWorker <- ZIO.succeed(ElevatorRequestWorker(List(insideElevator), ou, od))

        _ <- requestWorker.doWork(socketService)

        // Getting the size of the queue
        nonempty <- insideElevator.nonEmpty.commit

      } yield assertTrue(nonempty)
    }
  }
}


class TestSocketService(data: Queue[Either[Throwable, Chunk[Byte]]]) extends SocketService {
  override def readChunk(capacity: Int): Task[Chunk[Byte]] =
    data.take.flatMap {
      case Left(error) => ZIO.fail(error)
      case Right(value) => ZIO.succeed(value)
    }

  override def isOpen: Task[Boolean] = ZIO.succeed(false)
}

object TestSocketService {
  def create(data: List[Either[Throwable, Chunk[Byte]]]): ZIO[Any, Nothing, TestSocketService] =
    Queue.bounded[Either[Throwable, Chunk[Byte]]](data.size).flatMap { queue =>
      ZIO.foreach(data)(queue.offer).as(new TestSocketService(queue))
    }
}