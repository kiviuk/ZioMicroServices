package zio2.elevator

import zio.*
import zio.Console.*
import zio.nio.channels.*
import zio.nio.*

import java.util.concurrent.atomic.AtomicInteger

object Server extends ZIOAppDefault {

  case class Server() {
    val server = ZIO.scoped {
      AsynchronousServerSocketChannel.open
        .flatMap { socket =>
          for {
            address <- InetSocketAddress.hostName("127.0.0.1", 1337)
            _ <- socket.bindTo(address)
            _ <- socket
              .accept
              .flatMap(
                channel => doWork(TestChannel(channel)).catchAll(ex => printLine(ex.getMessage)).fork
              )
              .forever.fork
          } yield ()
        } *> ZIO.never
    }

    def doWork(channel: TestChannelService): ZIO[Any, Throwable, Unit] = {
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
        serverFiber <- server.fork
        _ <- ZIO.scoped(clientM.flatMap(_.writeChunk(Chunk.fromArray("12345".toCharArray.map(_.toByte)))))
        _ <- serverFiber.join
      } yield ()
  }

  val server = Server()

  def run: ZIO[Any, Exception, ExitCode] =
    for {
      _ <- server.start
      // handle termination signal
      _ <- ZIO.never.onInterrupt(ZIO.succeed(println("Server terminating.")))
    } yield ExitCode.success

}

trait TestChannelService {
  def readChunk(capacity: Int): Task[Chunk[Byte]]

  def isOpen: Task[Boolean]
}

case class TestChannel(channel: AsynchronousSocketChannel) extends TestChannelService {
  val count: AtomicInteger = AtomicInteger(0)

  override def readChunk(capacity: Int): Task[Chunk[Byte]] = channel.readChunk(capacity)

  override def isOpen: Task[Boolean] = ZIO.succeed(count.getAndIncrement() < 3) <* Console.printLine(count.get()-1)

}

object TestChannel {
  def apply(channel: AsynchronousSocketChannel) = new TestChannel(channel)
}