package zio2.elevator

import zio.stream.ZStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio.{ExitCode, Schedule, URIO, ZIOAppDefault, durationInt, Console}
import zio.http.{ServerSentEvent, HttpApp, Routes, Method, Response, handler, Server}
import zio.http.Middleware
import zio.ZIO

final case class TripDataPublisher(collector: ElevatorTripDataCollector) {

  def totalGlobalTripTime = for {
    totalTime <- collector.tripTimeTotalInMillis.map(time => s"TT=${time.toString}")
    totalTimeMax <- collector.tripTimeMaxInMillis.map(time => s"TTMAX=${time.toString}")
    totalTimeMin <- collector.tripTimeMinInMillis.map(time => s"TTMIN=${time.toString}")
    totalTimeMean <- collector.tripTimeMeanInMillis.map(time => s"TTMEAN=${time.toString}")
    totalNumberTrips <- collector.numberOfTrips.map(time => s"TNT=${time.toString}")
  } yield (ServerSentEvent(s"${totalTime};${totalTimeMax};${totalTimeMin};${totalTimeMean};${totalNumberTrips}"))

  val stream = ZStream.fromZIO(totalGlobalTripTime).repeat(Schedule.spaced(1.second).forever)
    
  val app =
    Routes(
      Method.GET / "sse" ->
        handler(Response.fromServerSentEvents(stream))
    ).toHttpApp @@ Middleware.dropTrailingSlash

  val run = {
    Server.serve(app).provide(Server.default)
  }
}

object TripDataPublisher {

    def apply(collector: ElevatorTripDataCollector) = new TripDataPublisher(collector)
}
