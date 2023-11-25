package ziomicroservices.elevator

import zio.nio.InetSocketAddress
import zio.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel}
import zio.stm.TPriorityQueue
import zio.{Console, ZIO}
import ziomicroservices.elevator.Decoder.decode
import ziomicroservices.elevator.model.{InsideElevatorRequest, OutsideDownRequest, OutsideUpRequest}
import ziomicroservices.elevator.Decoder.{Move, IncompleteCommand, UpRequest, DownRequest}

import java.io.IOException

case class DispatcherImpl(elevatorInsideQueues: List[TPriorityQueue[InsideElevatorRequest]],
                          ups: TPriorityQueue[OutsideUpRequest],
                          downs: TPriorityQueue[OutsideDownRequest]
                         ) {

  private val PORT = 7777
  private val HOST = "0.0.0.0"

  val server: ZIO[Any, IOException, Nothing] = ZIO.scoped {
    AsynchronousServerSocketChannel.open
      .flatMap { socket =>
        for {
          address <- InetSocketAddress.hostName(HOST, PORT)
          _ <- socket.bindTo(address).orElseFail(
            new IOException(s"Couldn't bind the server socket to the address $HOST:$PORT")
          )
          _ <- socket.accept.either.flatMap {
            case Left(ex) =>
              Console.printLine(s"Failed to accept client connection: ${ex.getMessage}")
            case Right(channel) =>
              doWork(channel).catchAll(ex =>
                Console.printLine(s"Exception in handling client: ${ex.getMessage}").unit
              ).fork
          }.forever
        } yield ()
      } *> ZIO.never
  }

  def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {

    def process(acc: String): ZIO[Any, Throwable, Unit] = {
      for {
        chunk <- channel.readChunk(3)
        cmd = acc + chunk.toArray.map(_.toChar).mkString

        _ <- ZIO.foreachDiscard(decode(cmd)) {
          case Move(elevatorId, floor) =>
            println(s"Moving 🛗 [$elevatorId] to 🏠 [$floor]")
            elevatorInsideQueues(elevatorId - 1).offer(InsideElevatorRequest(floor)).commit
          case UpRequest(floor) =>
            println(s"Requesting ⬆️ direction for 🏠 [$floor]")
            ups.offer(OutsideUpRequest(floor)).commit
          case DownRequest(floor) =>
            println(s"Requesting ⬇️ direction for 🏠 [$floor]")
            downs.offer(OutsideDownRequest(floor)).commit
          case IncompleteCommand(cmd) =>
            process(cmd)
        }
      } yield ()
    } // *> ZIO.fail(new RuntimeException("create artificial failure"))

    Console.printLine("{Dispatcher: Accepted a client connection, start working}")
      *> process("").whenZIO(channel.isOpen).forever

  }
}

object Dispatcher {
  def start(
             queues: List[TPriorityQueue[InsideElevatorRequest]],
             ups: TPriorityQueue[OutsideUpRequest],
             downs: TPriorityQueue[OutsideDownRequest]
           ) = {
    val dispatcher = DispatcherImpl(queues, ups, downs)
    dispatcher.server

  }
}