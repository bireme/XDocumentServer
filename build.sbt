lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      organization := "org.bireme",
      scalaVersion := "2.12.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "XDocumentServer"
  )

val pdfboxVersion = "2.0.12" // "2.0.9"
val pdfboxAppVersion = "2.0.12" // "2.0.9"
val jpef2000Version =  "1.3.0"
val jaiImageioCoreVersion = "1.4.0"
val sttpVersion = "1.3.9" // "1.2.1"
val vertxVersion = "3.5.4" // "3.5.2"
//val solrScalaClientVersion = "0.0.19"
val solrCellVersion = "7.5.0" // "7.4.0"
val restletVersion = "2.4.0"
val httpComponentsVersion = "4.5.6" // "4.5.5"
val scalajHttpVersion = "2.4.1"
val circeVersion = "0.10.0" // "0.9.3"
val commonsIOVersion = "2.6"
val hasherVersion = "1.2.0"
val airframeVersion = "0.69" // "0.52"
val hairyfotrVersion = "0.1.17"
val scalaTestVersion = "3.0.5"

resolvers += "Restlet Repositories" at "http://maven.restlet.org"

libraryDependencies ++= Seq(
  "org.apache.pdfbox" % "pdfbox" % pdfboxVersion,
  "org.apache.pdfbox" % "pdfbox-app" % pdfboxAppVersion,
  "com.github.jai-imageio" % "jai-imageio-jpeg2000" % jpef2000Version,
  "com.github.jai-imageio" % "jai-imageio-core" % jaiImageioCoreVersion,
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "io.vertx" %% "vertx-lang-scala" % vertxVersion,
  "io.vertx" %% "vertx-web-scala" % vertxVersion,
  //"com.github.takezoe" %% "solr-scala-client" % solrScalaClientVersion,
  "org.restlet.jee" % "org.restlet" % restletVersion,
  "org.apache.solr" % "solr-cell" % solrCellVersion,
  "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
  "org.scalaj" %% "scalaj-http" % scalajHttpVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "commons-io" % "commons-io" % commonsIOVersion,
  "com.roundeights" %% "hasher" % hasherVersion,
  "org.wvlet.airframe" %% "airframe-log" % airframeVersion,
  "org.scalatest" % "scalatest_2.12" % scalaTestVersion % "test"
)

logBuffered in Test := false
trapExit := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused")
addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % hairyfotrVersion)

/*
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
*/
