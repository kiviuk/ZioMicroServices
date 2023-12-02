package zio2.elevator.experiments

import zio.*
import zio.Console.*
import zio.nio.*
import zio.nio.channels.*

import java.io.IOException

object Server2 extends ZIOAppDefault {

  case class Server2() {

    val server2: ZIO[Any, IOException, Nothing] = ZIO.scoped {
      AsynchronousServerSocketChannel.open
        .flatMap { socket =>
          for {
            address <- InetSocketAddress.hostName("127.0.0.1", 1337)
            _ <- socket.bindTo(address)
            _ <- socket.accept.flatMap(channel => doWork(channel).catchAll(ex => printLine(ex.getMessage)).fork).forever.fork
          } yield ()
        } *> ZIO.never
    }

    def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {
      val process =
        for {
          chunk <- channel.readChunk(3)
          str = chunk.toArray.map(_.toChar).mkString
          _ <- printLine(s"received: [$str] [${chunk.length}]")
        } yield ()

      process.whenZIO(channel.isOpen).forever
    }

    val clientM: ZIO[Scope, Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel.open
      .flatMap { client =>
        for {
          host <- InetAddress.localHost
          address <- InetSocketAddress.inetAddress(host, 1337)
          _ <- client.connect(address)
        } yield client
      }


    def start =
      for {
        serverFiber <- server2.fork
        _ <- ZIO.scoped(clientM.flatMap(_.writeChunk(Chunk.fromArray(Array(65, 66, 67).map(_.toByte)))))
        _ <- serverFiber.join
      } yield ()

  }

  val server = Server2()


  def run: ZIO[Any, Exception, ExitCode] =
    for {
      _ <- server.start
      // handle termination signal
      _ <- ZIO.never.onInterrupt(ZIO.succeed(println("Server terminating.")))
    } yield ExitCode.success
}