/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{File, InputStream}
import java.net.URL

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http._
//import io.vertx.scala.core.streams.Pump
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}

import scala.util.{Failure, Success, Try}

class HttpThumbnailServer(localThumbnailServer: LocalThumbnailServer,
                          thumbnailServerPort: Int = 9090) {
  val vertx: Vertx = Vertx.vertx()
  val options: HttpServerOptions = HttpServerOptions().setPort(thumbnailServerPort).setLogActivity(true)
  var server: HttpServer = vertx.createHttpServer(options)
  val router: Router = Router.router(vertx)
  val routeGet: Route = router.route(HttpMethod.GET, "/getDocument").blockingHandler(handleGetDocument)
  val routeDelete: Route = router.route(HttpMethod.GET, "/deleteDocument").blockingHandler(handleDeleteDocument)
  server.requestHandler(router.accept _)
  server.listen()
  println(s"ThumbnailServer is listening port: $thumbnailServerPort")

  private def handleGetDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    val id: Option[String] = request.getParam("id")
    val url: Option[String] = request.getParam("url")

    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      response.setChunked(true)
      //Pump.pump(request, response).start()

      localThumbnailServer.getDocument(id.get, url) match {
        case Right(is) =>
          writeOutput(is, response) match {
            case Success(_) => response.putHeader("content-type", "image/jpeg").setStatusCode(200).end()
            case Failure(_) => response.setStatusCode(500).end("Error code: 500")
          }
        case Left(err) => response.setStatusCode(err).end(s"Error code: $err")
      }
    }
  }

  private def writeOutput(in: InputStream,
                          out: HttpServerResponse) : Try[Unit] = {
    Try {
      val arr: Array[Byte] = Tools.inputStream2Array(in).get
      out.write(Buffer.buffer(arr))
    }
  }

  private def handleDeleteDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    val id: Option[String] = request.getParam("id")

    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      //response.setChunked(true)
      val ret = localThumbnailServer.deleteDocument(id.get)
      response.setStatusCode(ret).end(s"document:${id.get} is deleted")
    }
  }
}

object HttpThumbnailServer extends App {
  private def usage(): Unit = {
    System.err.println("usage: HttpThumbnailServer -thumbDir=<dir> (-pdfDir=<dir>|-pdfDocServer=<url>) [-serverPort=<port>]")
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

  val serverPort = parameters.getOrElse("serverPort", "9090").toInt
  val documentServer = new FSDocServer(new File(parameters("thumbDir")))
  val localThumbnailServer: LocalThumbnailServer = parameters.get("pdfDir") match {
    case Some(pdfDir) => new LocalThumbnailServer(documentServer,
                                                  Right(new LocalPdfDocServer(new FSDocServer(new File(pdfDir)))))
    case None => parameters.get("pdfDocServer") match {
      case Some(url) => new LocalThumbnailServer(documentServer, Left(new URL(url)))
      case None => throw new IllegalArgumentException("(-pdfDir=<dir>|-pdfDocServer=<url>)")
    }
  }

  new HttpThumbnailServer(localThumbnailServer, serverPort)
  System.in.read()
}