package zio2.elevator

import zio.stm.TPriorityQueue
import zio.{Chunk, Queue, Task, UIO, ZIO}
import zio.test.{Spec, ZIOSpecDefault, assertTrue}
import zio2.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest}
import zio2.elevator.model.Request.*

import java.io.EOFException

object ElevatorRequestWorkerImplTest extends ZIOSpecDefault {

  def spec = suite("DecoderTest") {

    val cmdString = "m:1:4|m:1:4|r:1:u|r:1:d|junk|r:999:u"

    val data: List[Either[Throwable, Chunk[Byte]]] = cmdString.map { char =>
      Right(Chunk.single(char.toByte))
    }.toList

    val testSocketService: ZIO[Any, Nothing, SocketService] = TestSocketService.create(data)

    test("Worker should correctly decode valid commands, accept duplicates and ignore junk") {
      for {
        insideElevatorQueue <- makeQueue[InsideElevatorRequest]()
        outsideUpQueue <- makeQueue[OutsideUpRequest]()
        outsideDownQueue <- makeQueue[OutsideDownRequest]()
        socketService <- testSocketService
        requestWorker <- ZIO.succeed(ElevatorRequestWorker(List(insideElevatorQueue), outsideUpQueue, outsideDownQueue))

        _ <- requestWorker.doWork(socketService)

        // Getting the size of the queue
        insideQueueSize <- insideElevatorQueue.size.commit
        outsideUpQueueSize <- outsideUpQueue.size.commit
        outsideDownQueueSize <- outsideDownQueue.size.commit

      } yield assertTrue(insideQueueSize == 2, outsideUpQueueSize == 1, outsideDownQueueSize == 1)
    }
  }
}

class TestSocketService(inputDataStream: Queue[Either[Throwable, Chunk[Byte]]]) extends SocketService {

  override def readChunk(capacity: Int): Task[Chunk[Byte]] =
    inputDataStream.poll.flatMap {
      case Some(data) => data match
        case Left(err) => ZIO.fail(err)
        case Right(value) => ZIO.succeed(value)
      case _ => ZIO.fail(new EOFException("Channel has reached the end of stream"))
    }

  override def isOpen: Task[Boolean] = {
    ZIO.succeed(true)
  }
}

object TestSocketService {
  def create(data: List[Either[Throwable, Chunk[Byte]]]): ZIO[Any, Nothing, TestSocketService] =
    Queue.bounded[Either[Throwable, Chunk[Byte]]](data.size).flatMap { queue =>
      ZIO.foreach(data)(queue.offer).as(new TestSocketService(queue))
    }
}