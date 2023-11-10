package ziomicroservices.elevator.init

import scala.io.Source
import zio.json._
import ziomicroservices.elevator.model.Elevator

object ElevatorFileReader {
  def readElevators(path: String): Either[String, Set[Elevator]] = {

    val source = Source.fromInputStream(getClass.getResourceAsStream(path))

    try {
      val rawJson = source.mkString
      rawJson.fromJson[Set[Elevator]]
    //  rawJson.fromJson[Set[Elevator]].left.map(new Exception(_))

    } catch {
      case exception: Exception => Left(exception.getMessage)
    } finally {
      source.close()
    }
  }
}