resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "mavenRepoJX" at "http://repo1.maven.org/maven2/"
  //"oschina" at "http://maven.oschina.net/content/groups/public",
)

externalResolvers := Resolver.withDefaultResolvers(resolvers.value, mavenCentral = false)

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")