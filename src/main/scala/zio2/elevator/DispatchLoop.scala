package zio2.elevator

import zio.nio.InetSocketAddress
import zio.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel}
import zio.stm.TPriorityQueue
import zio.{Console, ZIO}
import zio2.elevator.Decoder.decodeCommand
import zio2.elevator.Decoder.{Move, IncompleteCommand, UpRequest, DownRequest}
import zio.ZLayer

import java.io.IOException

trait DispatchLoop {
    val startHandlingRequests: ZIO[Any, IOException, Nothing]
}

case class DispatchLoopImpl(dispatcher: Dispatcher) extends DispatchLoop {

  private val PORT = 7777
  private val HOST = "0.0.0.0"

  val startHandlingRequests: ZIO[Any, IOException, Nothing] = ZIO.scoped {
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
              dispatcher.doWork(LiveSocketService(channel)).catchAll(ex =>
                Console.printLine(s"Exception in handling client: ${ex.getMessage}")
              ).fork
          }.forever
        } yield ()
      } *> ZIO.never
  }
}

object DispatchLoop {
  def apply(dispatcher: Dispatcher) = new DispatchLoopImpl(dispatcher)
}
