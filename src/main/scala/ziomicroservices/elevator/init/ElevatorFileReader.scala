package ziomicroservices.elevator.init

import zio.json, zio.json.JsonDecoder, zio.json.DecoderOps
import ziomicroservices.elevator.model.{Elevator, ElevatorState}

import scala.io.Source
import scala.util.{Try, Using}

object ElevatorFileReader {
  private def readFile[T: JsonDecoder](path: String): Either[Throwable, Set[T]] = {
    Try {
      Using.resource(getClass.getResourceAsStream(path)) { stream =>
        val rawJson = Source.fromInputStream(stream).mkString
        rawJson.fromJson[Set[T]] match {
          case Right(value) => Right(value)
          case Left(error) => Left(new RuntimeException(s"Failed to parse JSON: $error"))
        }
      }
    } match {
      case scala.util.Success(value) => value
      case scala.util.Failure(exception) => Left(exception)
    }
  }


  def readElevators(path: String): Either[Throwable, Set[Elevator]] = readFile[Elevator](path)

  def readElevatorStates(path: String): Either[Throwable, Set[ElevatorState]] = readFile[ElevatorState](path)
}