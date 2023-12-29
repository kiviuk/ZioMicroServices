import zio2.elevator.SocketService
package zio2.elevator

import zio.stm.TPriorityQueue
import zio.test.{Spec, ZIOSpecDefault, assertTrue}
import zio2.elevator.Request.emptyChannel
import zio.ZIO
import zio.Chunk
import zio.Queue
import zio.Task
import {ElevatorRequestWorker, SocketService}

object ElevatorRequestWorkerImplTest extends ZIOSpecDefault {

  def spec = suite("DecoderTest") {

    val cmdString = "m:1:4|m:1:4|r:1:u|r:1:d|junk|r:999:u"

    val socketData: List[Either[Throwable, Chunk[Byte]]] = cmdString.map {
      char =>
        Right(Chunk.single(char.toByte))
    }.toList

    val testSocketService: ZIO[Any, Nothing, SocketService] =
      TestSocketService.create(socketData)

    test(
      "Worker should correctly decode valid commands, accept duplicates and ignore junk"
    ) {
      for {

        // arrange
        insideElevatorQueue <- emptyChannel[InsideElevatorRequest]
        outsideUpQueue <- emptyChannel[OutsideUpRequest]
        outsideDownQueue <- emptyChannel[OutsideDownRequest]
        socketService <- testSocketService
        requestWorker <- ZIO.succeed(
          ElevatorDispatcher(
            List(Elevator("", insideElevatorQueue)),
            outsideUpQueue,
            outsideDownQueue
          )
        )

        // act
        _ <- requestWorker.doWork(socketService)

        // assert
        insideQueueSize <- insideElevatorQueue.size.commit
        outsideUpQueueSize <- outsideUpQueue.size.commit
        outsideDownQueueSize <- outsideDownQueue.size.commit

      } yield assertTrue(
        insideQueueSize == 2,
        outsideUpQueueSize == 1,
        outsideDownQueueSize == 1
      )
    }
  }
}

class TestSocketService(inputDataStream: Queue[Either[Throwable, Chunk[Byte]]])
    extends SocketService {

  override def readChunk(capacity: Int): Task[Chunk[Byte]] =
    inputDataStream.poll.flatMap {
      case Some(data) =>
        data match
          case Left(err)    => ZIO.fail(err)
          case Right(value) => ZIO.succeed(value)
      case _ => ZIO.succeed(Chunk.empty)
    }

  override def isOpen: Task[Boolean] =
    inputDataStream.isEmpty.negate // .tap(b => printLine(s"IsOpen: $b"))
}

object TestSocketService {
  def create(
      data: List[Either[Throwable, Chunk[Byte]]]
  ): ZIO[Any, Nothing, SocketService] =
    Queue.bounded[Either[Throwable, Chunk[Byte]]](data.size).flatMap { queue =>
      ZIO.foreach(data)(queue.offer).as(new TestSocketService(queue))
    }
}
