/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File

import io.vertx.core.http.HttpMethod
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core.eventbus.EventBus
import io.vertx.scala.core.http.{HttpServer, HttpServerOptions, HttpServerRequest, HttpServerResponse}
import io.vertx.scala.core.{DeploymentOptions, MultiMap, Vertx}
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}

import scala.util.{Failure, Success}

class HttpPdfSrcServer(pdfSrcServer: LocalPdfSrcServer,
                       pdfServerPort: Int = 9191) {
  val vertx: Vertx = Vertx.vertx()
  val options: HttpServerOptions = HttpServerOptions().setPort(pdfServerPort).setLogActivity(true)
  val server: HttpServer = vertx.createHttpServer(options)
  val depOptions: DeploymentOptions = DeploymentOptions().setWorker(true)
  val eventBus: EventBus = vertx.eventBus()
  val router: Router = Router.router(vertx)
  val routePut: Route = router.route(HttpMethod.GET, "/pdfSrcServer/putDocument").handler(handlePutDocument)
  val routeDel: Route = router.route(HttpMethod.GET, "/pdfSrcServer/deleteDocument").handler(handleDeleteDocument)
  val routeInfo: Route = router.route(HttpMethod.GET, "/pdfSrcServer/infoDocument").handler(handleInfoDocument)
  val myVerticle = new PdfSrcVerticle(pdfSrcServer)

  implicit val executionContext: VertxExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())

  vertx.deployVerticle(myVerticle, depOptions)
  server.requestHandler(router.accept _)
  server.listen()
  println(s"PdfSrcServer is listening port: $pdfServerPort")

  private def handlePutDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response()
      .putHeader("content-type", "text/plain").setChunked(true)
    val params: MultiMap = request.params()
    val mapParams = params.names().foldLeft(Map[String, Set[String]]()) {
      case (map, name) => map + (name -> params.getAll(name).toSet)
    }

    if (mapParams.get("id").isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else if (mapParams.get("ur").isEmpty) {
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

  private def handleInfoDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response()
      .putHeader("content-type", "application/json")
    val params: MultiMap = request.params()
    val mapParams: Map[String, Set[String]] = params.names().foldLeft(Map[String, Set[String]]()) {
      case (map, name) => map + (name -> params.getAll(name).toSet)
    }

    val id = mapParams.get("id")
    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      eventBus.sendFuture[String]("org.bireme.xds.XDocServer.info", id.get.head).onComplete {
        case Success(result) =>
          val str = result.body()
          if (str.equals("**")) {
            response.setStatusCode(500).end("""{"error_code":"500"}""")
          } else {
            response.setStatusCode(200).end(result.body())
          }
        case Failure(err) =>
          response.setStatusCode(500).end(err.toString)
      }
    }
  }
}

object HttpPdfSrcServer extends App {
  private def usage(): Unit = {
    System.err.println("usage: HttpPdfSrcServer -pdfDir=<dir> -solrColUrl=<url> [-serverPort=<port>]")
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
  val solrColUrl: Option[String] = parameters.get("solrColUrl")
  val serverPort: Int = parameters.getOrElse("serverPort", "9191").toInt

  if (pdfDir.isEmpty) usage()
  if (solrColUrl.isEmpty) usage()

  val docServer = new FSDocServer(new File(pdfDir.get), Some("pdf"))
  //val docServer = new SwayDBServer(new File(pdfDir.get))
  val localPdfDocServer = new LocalPdfDocServer(docServer)
  val solrDocServer = new SolrDocServer(solrColUrl.get)
  val localPdfSrcServer = new LocalPdfSrcServer(solrDocServer, localPdfDocServer)

  new HttpPdfSrcServer(localPdfSrcServer, serverPort)
  System.in.read()
}