@main def generateStrings(): Unit ={
    import scala.util.Random

    val C = 1000  // replace with your desired number of repetitions

    val strings = (1 to C).map { _ =>
      // Randomly generate values
      val N = Random.nextInt(3) + 1  // Random integer between 1 and 2
      val X = if(Random.nextBoolean()) "u" else "d"  // Randomly select "u" or "d"
      val M1 = Random.nextInt(30) - 4  // Random integer between -4 and 25
      val M2 = Random.nextInt(30) - 4  // Random integer between -4 and 25

      // Concatenate values into the desired string format
      // s"m:$N:$M1|r:$M2:$X"
      s"m:$N:$M1"
    }.mkString("|")

    println(strings)
}
