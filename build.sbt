resolvers += "Bintary JCenter" at "http://jcenter.bintray.com"

libraryDependencies += "org.parboiled" %% "parboiled" % "2.1.4"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"

libraryDependencies += "org.jsoup" % "jsoup" % "1.10.2"

libraryDependencies ++= {
  Seq(
    "com.eclipsesource.j2v8" % "j2v8_win32_x86" % "4.6.0",
    "com.eclipsesource.j2v8" % "j2v8_linux_x86_64" % "4.6.0",
    "com.eclipsesource.j2v8" % "j2v8_win32_x86_64" % "4.6.0"
  )
}

libraryDependencies ++= {
  Seq(
    "org.webjars.npm" % "dustjs-linkedin" % "2.7.2" exclude("org.webjars.npm", "chokidar") exclude("org.webjars.npm", "cli")
  )
}

val circeVersion = "0.8.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

val playV = "2.6.6"

//libraryDependencies += "commons-io" % "commons-io" % "2.5"

//libraryDependencies += "com.typesafe.play" %% "play-guice" % playV
//lazy val playVersion = play.core.PlayVersion.current
libraryDependencies += "com.typesafe.play" %% "play" % playV
//lazy val ubwProject = RootProject(file("../ubw"))
scalaVersion := "2.12.2"
//dependsOn(ubwProject)
scalacOptions ++= Seq("-Ywarn-unused-import", "-deprecation")

name := "play-dust"