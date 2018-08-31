/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File
import java.net.URL

import io.vertx.core.http.HttpMethod
import io.vertx.scala.core.eventbus.EventBus
import io.vertx.scala.core.http.{HttpServer, HttpServerOptions, HttpServerRequest, HttpServerResponse}
import io.vertx.scala.core.{DeploymentOptions, MultiMap, Vertx}
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}

class HttpPdfSrcServer(pdfSrcServer: LocalPdfSrcServer,
                       pdfServerPort: Int = 9191) {
  val vertx: Vertx = Vertx.vertx()
  val options: HttpServerOptions = HttpServerOptions().setPort(pdfServerPort).setLogActivity(true)
  val server: HttpServer = vertx.createHttpServer(options)
  val depOptions: DeploymentOptions = DeploymentOptions().setWorker(true)
  val eventBus: EventBus = vertx.eventBus()
  val router: Router = Router.router(vertx)
  val routePut: Route = router.route(HttpMethod.GET, "/putDocument").handler(handlePutDocument)
  val routeDel: Route = router.route(HttpMethod.GET, "/deleteDocument").handler(handleDeleteDocument)
  val myVerticle = new PdfSrcVerticle(pdfSrcServer)

  vertx.deployVerticle(myVerticle, depOptions)
  server.requestHandler(router.accept _)
  server.listen()
  println(s"PdfSrcServer is listening port: $pdfServerPort")

  private def handlePutDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response()
      .putHeader("content-type", "text/plain").setChunked(true)
    val params: MultiMap = request.params()
    val mapParams = params.names().foldLeft(Map[String, Seq[String]]()) {
      case (map, name) => map + (name -> params.getAll(name))
    }

    if (mapParams.get("id").isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else if (mapParams.get("url").isEmpty) {
        response.setStatusCode(400).end("Missing url parameter")
    } else {
      val par: String = Tools.map2String(mapParams)
      eventBus.publish("org.bireme.xds.XDocServer.put", par)
      response.setStatusCode(200).end("Processing document")
    }
  }

  private def handleDeleteDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response()
      .putHeader("content-type", "text/plain")
    val params: MultiMap = request.params()
    val mapParams = params.names().foldLeft(Map[String, Set[String]]()) {
      case (map, name) => map + (name -> params.getAll(name).toSet)
    }

    val id = mapParams.get("id")
    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      eventBus.publish("org.bireme.xds.XDocServer.delete", id.get.head)
      response.setStatusCode(200).end("Deleting document")
    }
  }
}

object HttpPdfSrcServer extends App {
  private def usage(): Unit = {
    System.err.println("usage: HttpPdfSrcServer (-pdfDir=<dir>|-pdfDocServer=<url>) -solrColUrl=<url> [-serverPort=<port>]")
    System.exit(1)
  }

  if (args.length < 2) usage()

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

  val pdfDir: Option[String] = parameters.get("pdfDir")
  val pdfDocServer: Option[String] = parameters.get("pdfDocServer")
  val solrColUrl: Option[String] = parameters.get("solrColUrl")
  val serverPort: Int = parameters.getOrElse("serverPort", "9191").toInt

  if (pdfDir.isEmpty && pdfDocServer.isEmpty) usage()
  if (solrColUrl.isEmpty) usage()

  val localPdfDocServer = new LocalPdfDocServer(new FSDocServer(new File(pdfDir.get)))
  val solrDocServer = new SolrDocServer(solrColUrl.get)
  val localPdfSrcServer =
    if (pdfDir.isDefined)
      new LocalPdfSrcServer(solrDocServer, Right(new LocalPdfDocServer(new FSDocServer(new File(pdfDir.get)))))
    else
      new LocalPdfSrcServer(solrDocServer, Left(new URL(pdfDocServer.get)))

  new HttpPdfSrcServer(localPdfSrcServer, serverPort)
  System.in.read()
}