package zio2.elevator

import zio.stm.TPriorityQueue
import zio2.elevator.Decoder.decode
import zio2.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest}
import zio2.elevator.Decoder.{DownRequest, IncompleteCommand, Move, UpRequest}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, Task, ZIO, Console}

trait ElevatorRequestWorker {
  def doWork(channel: SocketService): ZIO[Any, Throwable, Unit]
}

case class ElevatorRequestWorkerImpl(elevatorInsideQueues: List[TPriorityQueue[InsideElevatorRequest]],
                                     ups: TPriorityQueue[OutsideUpRequest],
                                     downs: TPriorityQueue[OutsideDownRequest]) extends ElevatorRequestWorker {

  override def doWork(channel: SocketService): ZIO[Any, Throwable, Unit] = {

    def process(acc: String): ZIO[Any, Throwable, Unit] = {
      for {

        chunk <- channel.readChunk(3)
        cmd = acc + chunk.toArray.map(_.toChar).mkString

        _ <- ZIO.foreachDiscard(decode(cmd)) {
          case Move(elevatorId, floor) if elevatorId <= elevatorInsideQueues.size =>
            println(s"Moving 🛗 [$elevatorId] to 🏠 [$floor]")
            elevatorInsideQueues(elevatorId - 1).offer(InsideElevatorRequest(floor)).commit
          case UpRequest(floor) =>
            println(s"Requesting ⬆️ direction from 🏠 [$floor]")
            ups.offer(OutsideUpRequest(floor)).commit
          case DownRequest(floor) =>
            println(s"Requesting ⬇️ direction from 🏠 [$floor]")
            downs.offer(OutsideDownRequest(floor)).commit
          case IncompleteCommand(cmd) =>
            process(cmd)
          case _ =>
            ZIO.unit
        }
      } yield ()
    } // *> ZIO.fail(new RuntimeException("create artificial failure"))

    Console.printLine("{Dispatcher: Accepted a client connection, start working}")
      *> process("").whenZIO(channel.isOpen).forever.catchAll { error =>
      ZIO.logError(s"${error.getMessage}").as(ZIO.succeed(""))
    }

  }
}

object ElevatorRequestWorker {
  def apply(elevatorInsideQueues: List[TPriorityQueue[InsideElevatorRequest]],
            outsideUpRequestQueue: TPriorityQueue[OutsideUpRequest],
            outsideDownRequestQueue: TPriorityQueue[OutsideDownRequest]): ElevatorRequestWorker =
    ElevatorRequestWorkerImpl(elevatorInsideQueues, outsideUpRequestQueue, outsideDownRequestQueue)
}

///////////////////////////////////////////////////////////////////////////////////////////////////

trait SocketService {
  def readChunk(capacity: Int): Task[Chunk[Byte]]
  def isOpen: Task[Boolean]
}

///////////////////////////////////////////////////////////////////////////////////////////////////

class LiveSocketService(socket: AsynchronousSocketChannel) extends SocketService {
  override def readChunk(capacity: Int): Task[Chunk[Byte]] = socket.readChunk(capacity)
  override def isOpen: Task[Boolean] = socket.isOpen
}

object LiveSocketService {
  def apply(socket: AsynchronousSocketChannel): LiveSocketService = new LiveSocketService(socket)
}