lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      organization := "org.bireme",
      scalaVersion :=  "2.12.8",  // "2.13.1", vertx não tem
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "XDocumentServer"
  )

val pdfboxVersion = "2.0.17" //"2.0.16"
val pdfboxAppVersion = "2.0.17" //"2.0.16"
val jpef2000Version =  "1.3.0"
val jaiImageioCoreVersion = "1.4.0"
val sttpVersion = "1.7.2" //"1.6.7"
val vertxVersion = "3.8.0" //"3.8.4" nao tem para scala 2.13
val solrCellVersion = "8.3.1" //"8.2.0"
val restletVersion = "2.4.2" //"2.4.0"
val httpComponentsVersion = "4.5.10" //"4.5.9"
val scalajHttpVersion = "2.4.2" //"2.4.1"
val circeVersion = "0.12.3" //"0.12.1"
val commonsIOVersion = "2.6"
val hasherVersion = "1.2.0"
val airframeVersion = "19.9.7" //"19.6.1"
val hairyfotrVersion = "0.1.17"
val scalaTestVersion = "3.1.0" //"3.0.8"
//val swaydbVersion = "0.10.9" // "0.6"

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
  //"io.swaydb" %% "swaydb" % swaydbVersion
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
