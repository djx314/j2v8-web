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

libraryDependencies += "commons-io" % "commons-io" % "2.5"

libraryDependencies += "com.google.inject" % "guice" % "4.1.0"
val playV = "2.6.0"
//lazy val playVersion = play.core.PlayVersion.current

libraryDependencies += "com.typesafe.play" %% "play" % playV

lazy val fsnProject = RootProject(file("../fsn"))

scalaVersion := "2.11.11"

dependsOn(fsnProject)

scalacOptions ++= Seq("-Ywarn-unused-import", "-deprecation")