package ziomicroservices.elevator


import zio.*
import zio.stm.{STM, TPriorityQueue}


object Main extends ZIOAppDefault {

  def dummyLogger(feed: TPriorityQueue[Int]) = {
    feed.size.commit.map(println)
  }

  private case class Listener(name: String, feed: TPriorityQueue[Int]) {
    def listen = (for {

      _ <- dummyLogger(feed)
      _ <- STM.atomically {
        feed.take
      }
      drained <- feed.isEmpty.commit

    } yield drained).repeatWhile(!_)
  }

  private def program = {

    implicit val ordering: Ordering[Int] = (x: Int, y: Int) => x.compareTo(y)

    for {

      podcast <- TPriorityQueue.make[Int](1, 2, 3, 4, 5, 6, 7).commit
      listener = Listener("Paula", podcast)
      _ <- listener.listen
      _ <- Console.readLine("Press any key to exit...")

    } yield ()
  }

  def run = program


}