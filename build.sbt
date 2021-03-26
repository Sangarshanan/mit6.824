val json4sJackson = "org.json4s" %% "json4s-native" % "3.6.10"
ThisBuild / scalaVersion := "2.13.0"


lazy val root = (project in file("."))
  .settings(
    name := "distributed-system",
    version := "1.0",
    libraryDependencies += json4sJackson
  )
