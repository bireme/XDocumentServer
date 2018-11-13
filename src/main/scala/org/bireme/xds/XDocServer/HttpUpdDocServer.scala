/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer
import io.vertx.core.http.HttpMethod
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServer, HttpServerOptions, HttpServerRequest, HttpServerResponse}
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}

class HttpUpdDocServer(updServerPort: Int = 9292,
                       pdfDocDir: String,
                       solrColUrl: Option[String],
                       thumbDir: Option[String]) {
  val vertx: Vertx = Vertx.vertx()
  val options: HttpServerOptions = HttpServerOptions().setPort(updServerPort).setLogActivity(true)
  val server: HttpServer = vertx.createHttpServer(options)
  val router: Router = Router.router(vertx)
  val routeGet: Route = router.route(HttpMethod.GET, "/addDocServer/updDocument").blockingHandler(handleGetDocument)

  server.requestHandler(router.accept _)
  server.listen()
  println(s"HttpUpdDocServer is listening port: $updServerPort")

  private def handleGetDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    val id: Option[String] = request.getParam("id")

    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      val updDocs = new UpdateDocuments(pdfDocDir,solrColUrl,thumbDir, None)
      if (updDocs.updateOne(id.get)) {
        response.putHeader("content-type", "text/plain").setStatusCode(201).end("Document (created|updated)")
      } else response.setStatusCode(500).end("Error code: 500")
    }
  }
}

object HttpUpdDocServer extends App {
  private def usage(): Unit = {
    System.err.println("usage: HttpUpdDocServer -pdfDocDir=<dir> [-solrColUrl=<url>] [-thumbDir=<dir>] [-serverPort=<port>]")
    System.exit(1)
  }

  if (args.length < 1) usage()

  // Parse parameters
  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else {
        usage()
        map
      }
  }

  val pdfDocDir: Option[String] = parameters.get("pdfDocDir")
  val solrColUrl: Option[String] = parameters.get("solrColUrl")
  val thumbDir: Option[String] = parameters.get("thumbDir")
  val updServerPort: Int = parameters.getOrElse("serverPort", "9292").toInt

  if (pdfDocDir.isEmpty) usage()

  new HttpUpdDocServer(updServerPort, pdfDocDir.get, solrColUrl, thumbDir)
  System.in.read()
}