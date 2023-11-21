package ziomicroservices.elevator.service.elevator

import zio.nio.InetSocketAddress
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.channels.AsynchronousSocketChannel
import zio.stream.*
import zio.{Chunk, Clock, Console, ExitCode, RIO, Scope, Trace, UIO, ZIO, ZIOAppDefault, durationInt}

import java.io.IOException

case class DispatcherImpl() {

  val server: ZIO[Any, IOException, Nothing] = ZIO.scoped {
    AsynchronousServerSocketChannel.open
      .flatMap { socket =>
        for {
          address <- InetSocketAddress.hostName("127.0.0.1", 7777)
          _ <- socket.bindTo(address)
          _ <- socket.accept.either.flatMap {
            case Left(ex) =>
              Console.printLine(s"Failed to accept client connection: ${ex.getMessage}")
            case Right(channel) =>
              Console.printLine(s"Accepted a client connection") *> doWork(channel).catchAll(ex => Console.printLine(s"Exception in handling client: ${ex.getMessage}")).fork
          }.forever.fork
        } yield ()
      }.catchAll(ex => Console.printLine(s"Failed to establish server: ${ex.getMessage}")) *> ZIO.never
  }

  def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {

    // echo "Hello|world|123|456" | nc  -w 0 localhost 7777
    def process(acc: String): ZIO[Any, Throwable, Unit] = {
      for {
        chunk <- channel.readChunk(3) // Adjust the chunk size as per your requirement
        str = acc + chunk.toArray.map(_.toChar).mkString
        _ <- Console.printLine(s"current acc: [$acc]")
        _ <- Console.printLine(s"current str: [$str]")
        lastPipeIndex = str.lastIndexOf('|')
        _ <- if (lastPipeIndex != -1) {
          val printPart = str.substring(0, lastPipeIndex)
          val remainingPart = str.substring(lastPipeIndex + 1)
          Console.printLine(s"received: [$printPart]") *> process(remainingPart)
        } else {
          Console.printLine(s"no pipe symbol, waiting for more data...") *> process(str)
        }
      } yield ()
    }

    Console.printLine("Accepted a client connection, start working") *> process("").whenZIO(channel.isOpen).forever

  }
}

object Dispatcher {
  def start: ZIO[Scope, IOException, Unit] = {
    val dispatcher = DispatcherImpl()
    dispatcher.server
  }
}