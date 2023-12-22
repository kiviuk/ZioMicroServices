package zio2.elevator

import zio.Console.printLine
import zio.stm.TPriorityQueue
import zio2.elevator.Decoder.{Command, DownRequest, IncompleteCommand, Move, UpRequest, decodeCommand}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, Console, Task, ZIO}
import zio.ZLayer

trait ElevatorRequestWorkerTrait {
  def doWork(channel: SocketService): ZIO[Any, Throwable, Unit]
}

case class ElevatorRequestWorkerImpl(elevators: List[Elevator],
                                     upQueue: TPriorityQueue[OutsideUpRequest],
                                     downQueue: TPriorityQueue[OutsideDownRequest]) extends ElevatorRequestWorkerTrait {
  override def doWork(channel: SocketService): ZIO[Any, Throwable, Unit] = {
    def handleCommand(cmd: Command, isChannelOpen: Boolean): ZIO[Any, Throwable, Unit] = {
      cmd match {
        case Move(elevatorId, floor) if elevatorId <= elevators.size =>
          elevators(elevatorId - 1).insideQueue.offer(InsideElevatorRequest(floor, elevatorTripData = ElevatorTripData())).commit *>
            printLine(s"Moving 🛗 [$elevatorId] to 🏠 [$floor] ")
        case UpRequest(floor) =>
          upQueue.offer(OutsideUpRequest(floor, elevatorTripData = ElevatorTripData())).commit *>
            printLine(s"Requesting ⬆️ direction from 🏠 [$floor]")
        case DownRequest(floor) =>
          downQueue.offer(OutsideDownRequest(floor, elevatorTripData = ElevatorTripData())).commit *>
            printLine(s"Requesting ⬇️ direction from 🏠 [$floor]")
        case IncompleteCommand(cmd) if isChannelOpen =>
          process(cmd) /* *>
            printLine(s"Command: $cmd") */
        case _ =>
          ZIO.unit
      }
    }

    def process(acc: String): ZIO[Any, Throwable, Unit] = {
      for {
        chunk <- channel.readChunk(3000)
        cmd = acc + chunk.toArray.map(_.toChar).mkString
        isChannelOpen <- channel.isOpen
        commands = decodeCommand(cmd)
        _ <- ZIO.foreachDiscard(commands)(command => handleCommand(command, isChannelOpen))
      } yield ()
    }

    for {
      _ <- printLine("{Dispatcher: Accepting client connection, start working}")
      _ <- process("").repeatWhileZIO( _ => channel.isOpen.catchAll { ex => ZIO.logError(s"${ex.getMessage}") *> ZIO.succeed(true) })
    } yield ()

  }
}

object ElevatorRequestWorkerTrait {
  def apply(elevators: List[Elevator],
            outsideUpRequestQueue: TPriorityQueue[OutsideUpRequest],
            outsideDownRequestQueue: TPriorityQueue[OutsideDownRequest]): ElevatorRequestWorkerTrait =
    ElevatorRequestWorkerImpl(elevators, outsideUpRequestQueue, outsideDownRequestQueue)
}

object ElevatorRequestWorker {
  def apply(elevators: List[Elevator],
            outsideUpRequestQueue: TPriorityQueue[OutsideUpRequest],
            outsideDownRequestQueue: TPriorityQueue[OutsideDownRequest]) =
      ElevatorRequestWorkerImpl(elevators, outsideUpRequestQueue, outsideDownRequestQueue)
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