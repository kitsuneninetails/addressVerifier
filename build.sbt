lazy val root = (project in file("."))
  .settings(
      name := "AddressServer",
      scalaVersion := "2.12.1",
      libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.17",
      libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-http-core" % "10.0.4",
          "com.typesafe.akka" %% "akka-http" % "10.0.4",
          "com.typesafe.akka" %% "akka-http-testkit" % "10.0.4",
          "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.4",
          "com.typesafe.akka" %% "akka-http-jackson" % "10.0.4",
          "com.typesafe.akka" %% "akka-http-xml" % "10.0.4")
)
