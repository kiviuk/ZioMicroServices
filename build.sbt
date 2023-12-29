ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val zioVersion = "2.0.19"

lazy val root = (project in file("."))
  .settings(
    name := "ZioMicroServices",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork := true
  )

libraryDependencies ++= Seq(
  "dev.zio"            %% "zio"              % zioVersion,
  "dev.zio"            %% "zio-json"         % "0.6.0",
  "dev.zio"            %% "zio-http"         % "3.0.0-RC4",
  "dev.zio"            %% "zio-connect-file"  % "0.4.4",
  "dev.zio"            %% "zio-logging"      % "2.1.15",
  "dev.zio"            %% "zio-nio"          % "2.0.2"
)

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-math3"     % "3.6.1"
)

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % zioVersion  % Test,
  "dev.zio" %% "zio-test-sbt"      % zioVersion  % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion  % Test,
  "dev.zio" %% "zio-http-testkit"  % "3.0.0-RC3" % Test
)

scalacOptions += "-explain"

Compile / run / mainClass := Some("zio2.elevator.ElevatorMainApp")