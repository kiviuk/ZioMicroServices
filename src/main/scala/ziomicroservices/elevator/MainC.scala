package ziomicroservices.elevator


import zio.*
import zio.stm.{STM, TPriorityQueue}


object MainC extends ZIOAppDefault {
  object Request {
    implicit val ordering: Ordering[Int] = (x: Int, y: Int) => x.compareTo(y)
  }

  def dummyLogger(feed: TPriorityQueue[Int]) = {
      feed.size.commit.map(println)
  }

  private case class Listener(name: String, feed: TPriorityQueue[Int]) {
    def listen = (for {

      _ <- dummyLogger(feed)

      data <- STM.atomically {
        feed.take
      }

//      _ <- ZIO.debug(s"Listening($data)").as(data)
      drained <- feed.isEmpty.commit
//      _ <- ZIO.debug(s"drained=$drained").as(drained)

    } yield drained).repeatWhile(!_)
  }

  private def program = {

    for {

      podcast <- TPriorityQueue.make[Int](1, 2, 3, 4, 5, 6, 7).commit
      listener = Listener("Paula", podcast)
      _ <- listener.listen

//      _ <- listener.feed.offerAll(List(-1)).commit
      _ <- listener.listen
      _ <- Console.readLine("Press any key to exit...")

    } yield ()
  }

  def run = program


}