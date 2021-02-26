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
  val routeHello: Route = router.route(HttpMethod.GET, "/").blockingHandler(handleHello)
  val routeGet: Route = router.route(HttpMethod.GET, "/thumbnailServer/getDocument").blockingHandler(handleGetDocument)
  val routeGetRest: Route = router.route(HttpMethod.GET, "/thumbnailServer/getDocument/:id").blockingHandler(handleGetDocumentRest)
  val routeDelete: Route = router.route(HttpMethod.GET, "/thumbnailServer/deleteDocument").blockingHandler(handleDeleteDocument)
  server.requestHandler(router.accept _)
  server.listen()
  println(s"ThumbnailServer is listening port: $thumbnailServerPort")

  private def handleHello(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    response.setStatusCode(200).end("HttpThumbnailServer is ok!")
  }

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
            case Success(_) => response.putHeader("Cache-Control", "public").
              putHeader("content-type", "image/jpeg").setStatusCode(200).end()
            case Failure(_) => response.setStatusCode(500).end("Error code: 500")
          }
        case Left(err) => response.setStatusCode(err).end(s"Error code: $err")
      }
    }
  }

  private def handleGetDocumentRest(routingContext: RoutingContext): Unit = {
    val request: HttpServerRequest = routingContext.request()
    val response: HttpServerResponse = routingContext.response().putHeader("content-type", "text/plain")
    val id: Option[String] = request.getParam("id")

    if (id.isEmpty) {
      response.setStatusCode(400).end("Missing id parameter")
    } else {
      //response.setChunked(true)
      //Pump.pump(request, response).start()

      /*val headers = request.headers()
      headers.names.foreach {
        name => println(s"key=$name values=${headers.getAll(name)}")
      }*/

      localThumbnailServer.getDocument(id.get, None) match {
        case Right(is) =>
          Try {
            val arr: Array[Byte] = Tools.inputStream2Array(is).get
            //response.putHeader("Cache-Control", "public").
            response.
              putHeader("Date", "Tue, 07 Nov 2018 11:23:26 GMT").
              putHeader("Server", "Apache/2.2.13 (Red Hat)").
              //putHeader("Last-Modified:", "Mon, 20 Aug 2018 20:43:26 GMT").
              putHeader("ETag", "\"7b41a7-c574-573e3f6075780\"").
              putHeader("Accept-Ranges", "bytes").
              putHeader("Content-Length", arr.length.toString).
              putHeader("Vary", "User-Agent").
              putHeader("Keep-Alive", "timeout=5, max=100").
              putHeader("Connection", "Keep-Alive").
              putHeader("Content-Type", "image/jpeg")

            response.write(Buffer.buffer(arr))
          } match {
            case Success(_) => response.setStatusCode(304).end()
            case Failure(ex) => response.setStatusCode(500).end(s"Error code: 500. [${ex.getMessage}]")
          }
        case Left(err) => response.setStatusCode(err).end(s"Error code: $err")
      }
    }
  }

  private def writeOutput(in: InputStream,
                          out: HttpServerResponse): Try[Unit] = {
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
  val documentServer = new FSDocServer(new File(parameters("thumbDir")), Some("jpg"))
  //val documentServer = new SwayDBServer(new File(parameters("thumbDir")))
  val localThumbnailServer: LocalThumbnailServer = parameters.get("pdfDir") match {
    case Some(pdfDir) =>
      val pdfDocServer = new FSDocServer(new File(pdfDir), Some("pdf"))
      //val pdfDocServer = new SwayDBServer(new File(parameters(pdfDir)))
      new LocalThumbnailServer(documentServer, Some(Right(new LocalPdfDocServer(pdfDocServer))))
    case None => parameters.get("pdfDocServer") match {
      case Some(url) => new LocalThumbnailServer(documentServer, Some(Left(new URL(url))))
      case None => throw new IllegalArgumentException("(-pdfDir=<dir>|-pdfDocServer=<url>)")
    }
  }

  new HttpThumbnailServer(localThumbnailServer, serverPort)
  System.in.read()
}