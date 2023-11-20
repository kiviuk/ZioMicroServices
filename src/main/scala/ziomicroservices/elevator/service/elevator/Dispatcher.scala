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
          _ <- socket.accept.flatMap(
            channel => doWork(channel).catchAll(ex => Console.printLine(ex.getMessage)).fork
          ).forever.fork
        } yield ()
      } *> ZIO.never
  }

  private def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {
    var x = Chunk.newBuilder[Byte].result()
    val process =
      for {
        chunk <- channel.readChunk(10)
        (a,b) = chunk.splitWhere(_.toString.equals('\n'.toByte))

//        xb = x.++(b)


        strA = a.toArray.map(_.toChar).mkString
        strB = b.toArray.map(_.toChar).mkString
        _ <- Console.printLine(s"received: A [$strA] [${a.length}]")
        _ <- Console.printLine(s"received: B [$strB] [${b.length}]")
        //_ <- channel.writeChunk(Chunk.fromArray("Received".getBytes))
      } yield ()

    process.whenZIO(channel.isOpen).forever
  }

}

object Dispatcher {
  for {
    _ <- DispatcherImpl().server
  } yield ()
}