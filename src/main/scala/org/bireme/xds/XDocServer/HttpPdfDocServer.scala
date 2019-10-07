/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{File, InputStream}

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServer, HttpServerOptions, HttpServerRequest, HttpServerResponse}
//import io.vertx.scala.core.streams.Pump
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}

import scala.util.{Failure, Success, Try}

class HttpPdfDocServer(pdfDocServer: LocalPdfDocServer,
                       pdfServerPort: Int = 8989) {
  val vertx: Vertx = Vertx.vertx()
  val options: HttpServerOptions = HttpServerOptions().setPort(pdfServerPort).setLogActivity(true)
  val server: HttpServer = vertx.createHttpServer(options)
  val router: Router = Router.router(vertx)
  val routeGet: Route = router.route(HttpMethod.GET, "/pdfDocServer/getDocument").blockingHandler(handleGetDocument)
  val routeDelete: Route = router.route(HttpMethod.GET, "/pdfDocServer/deleteDocument").blockingHandler(handleDeleteDocument)

  server.requestHandler(router.accept _)
  server.listen()
  println(s"PdfDocServer is listening port: $pdfServerPort")

  private def handleGetDocument(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    val id: Option[String] = request.getParam("id")
    val url: Option[String] = request.getParam("url")

    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      response.setChunked(true)
      //println("pump antes")
      //Pump.pump(request, response).start()
      //println("pump depois")
      pdfDocServer.getDocument(id.get, url) match {
        case Right(is) =>
          writeOutput(is, response) match {
            case Success(_) =>
              response.putHeader("content-type", "application/pdf").setStatusCode(200).end()
            case Failure(_) =>
              response.setStatusCode(500).end("Error code: 500")
          }
        case Left(err) =>
          response.setStatusCode(err).end(s"Error code: $err")
      }
    }
  }

  private def writeOutput(in: InputStream,
                          out: HttpServerResponse) : Try[Unit] = {
    Try {
      var continue = true
      val array = Array.ofDim[Byte](1024)
      val buffer: Buffer = Buffer.buffer()

      while(continue) {
        in.read(array) match {
          case -1 =>
            in.close()
            //out.close()
            continue = false
          case read =>
            buffer.appendBytes(array, 0, read)
        }
      }
      out.write(buffer)
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
      val ret = pdfDocServer.deleteDocument(id.get)
      response.setStatusCode(ret).end(s"document:${id.get} is deleted")
    }
  }
}

object HttpPdfDocServer extends App {
  private def usage(): Unit = {
    System.err.println("usage: HttpPdfDocServer -pdfDir=<dir> [-serverPort=<port>]")
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

  val pdfDir: Option[String] = parameters.get("pdfDir")
  //val serverPort: Int = parameters.getOrElse("serverPort", "8989").toInt
  val serverPort: Int = parameters.getOrElse("serverPort", "9292").toInt

  if (pdfDir.isEmpty) usage()

  val pdfDocServer = new FSDocServer(new File(parameters(pdfDir.get)), Some("pdf"))
  //val pdfDocServer = new SwayDBServer(new File(parameters(pdfDir.get)))
  val localPdfDocServer = new LocalPdfDocServer(pdfDocServer)

  new HttpPdfDocServer(localPdfDocServer, serverPort)
  System.in.read()
}
