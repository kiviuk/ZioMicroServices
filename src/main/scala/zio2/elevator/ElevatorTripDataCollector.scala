package zio2.elevator

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import zio.{ZIO, Console, Ref}
import java.io.IOException

trait ElevatorTripDataCollector {
  def add(newTripData: ElevatorTripData): ZIO[Any, Nothing, Unit]

  def addAll(tripDataList: Seq[ElevatorTripData]): ZIO[Any, Nothing, Unit]

  def tripTimeMeanInMillis: ZIO[Any, Nothing, Double]

  def tripTimeTotalInMillis: ZIO[Any, Nothing, Double]

  def tripTimeMinInMillis: ZIO[Any, Nothing, Double]

  def tripTimeMaxInMillis: ZIO[Any, Nothing, Double]

  def numberOfTrips: ZIO[Any, Nothing, Int]

}

case class ElevatorTripDataCollectorImpl(tripDataRef: Ref[Vector[ElevatorTripData]])
    extends ElevatorTripDataCollector {
  override def add(newTripData: ElevatorTripData) =
    tripDataRef.update(storage => storage :+ newTripData)

  override def addAll(tripDataList: Seq[ElevatorTripData]) =
    tripDataRef.update(storage => storage.appendedAll(tripDataList))

  def tripTimeStatistics = {
    val tripTimeStats = new DescriptiveStatistics()

    for {
      storage <- tripDataRef.get
      _ <- ZIO.succeed(storage.map(elevatorTripData => {
        elevatorTripData.getTripTimeInMillis match
          case Some(tripTime) => tripTimeStats.addValue(tripTime)
          case None           => ZIO.unit
      }))

      // _ <- ZIO.foreach(tripTimeStats.getValues())(b => Console.printLine(s"TTS=$b").orDie)

    } yield tripTimeStats

  }

  override def tripTimeMeanInMillis = tripTimeStatistics.map { _.getMean() }

  override def tripTimeTotalInMillis = tripTimeStatistics.map { _.getSum() }

  override def tripTimeMaxInMillis = tripTimeStatistics.map { _.getMax() }

  override def tripTimeMinInMillis = tripTimeStatistics.map { _.getMin() }

  override def numberOfTrips = tripTimeStatistics.flatMap { x =>
    ZIO.succeed(x.getN().toInt)
  }
}
object ElevatorTripDataCollector {
  def apply(
      storage: Ref[Vector[ElevatorTripData]]
  ): ElevatorTripDataCollector = new ElevatorTripDataCollectorImpl(
    storage
  )
}