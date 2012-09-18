import sbt._
import Keys._

object PracticeBuild extends Build {
  lazy val practiceProject = Project("practice", file(".")).settings(

    scalaVersion := "2.9.1",

    scalacOptions += "-Ydependent-method-types",

    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-core" % "6.0.4",
      "javax.jdo" % "jdo-api" % "3.0",
      "com.typesafe.akka" % "akka-actor" % "2.0.3",
      //"com.typesafe.akka" % "akka-transactor" % "2.0.3",
      //"com.db4o" % "db4o-full-java5" % "8.0-SNAPSHOT",
      "com.orientechnologies" % "orient-commons" % "1.0.1",
      "com.orientechnologies" % "orientdb-core" % "1.0.1",
      "com.orientechnologies" % "orientdb-object" % "1.0.1",
      "com.chuusai" %% "shapeless" % "1.2.2"
    ),
    resolvers ++= Seq(
      "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
      "Typesafe Repository" at "https://typesafe.artifactoryonline.com/typesafe/releases/",
      "Sonatype groups/public" at "https://oss.sonatype.org/content/groups/public/"
      //"Versant db4o Repository" at "https://source.db4o.com/maven/"
    )
  )
}
