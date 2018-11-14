/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import io.circe.Json
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.EventBus
import wvlet.log.LogSupport

import scala.util.Try

class PdfSrcVerticle(pdfSrcServer: LocalPdfSrcServer) extends ScalaVerticle with LogSupport {
  override def start(): Unit = {
    val eb: EventBus = vertx.eventBus()
    println("PdfSrcVerticle is now running ...")

    eb.consumer(
      "org.bireme.xds.XDocServer.put",
      (message: io.vertx.scala.core.eventbus.Message[String]) => {
        println("Processing put request...")
        Try {
          val mapParams: Map[String, Seq[String]] = Tools.string2Map(message.body())
          val id: Option[Seq[String]] = mapParams.get("id")
          val url: Option[Seq[String]] = mapParams.get("ur")

          if (id.isEmpty || url.isEmpty) error("id.isEmpty || url.isEmpty")
          else {
            pdfSrcServer.replaceDocument(id.get.head, url.get.head, Some(mapParams)) match {
              case 500 =>
                error(
                  s"Document creation: id=${id.get.head} url=${url.get.head} params=$mapParams"
                )
              case _ => info(s"Document created: id=${id.get} url=${url.get}")
            }
          }
        } match {
          case scala.util.Success(_) => ()
          case scala.util.Failure(exception) => error(exception.getMessage)
        }
      }
    )

    eb.consumer(
      "org.bireme.xds.XDocServer.delete",
      (message: io.vertx.scala.core.eventbus.Message[String]) => {
        println("Processing delete request...")

        val id: String = message.body()

        if (id.isEmpty) error("id.isEmpty")
        else {
          pdfSrcServer.deleteDocument(id) match {
            case 200 => info(s"Document deleted: id=$id")
            case err => error(s"Document not deleted: code=$err id=$id")
          }
        }
      }
    )

    eb.consumer(
      "org.bireme.xds.XDocServer.info",
      (message: io.vertx.scala.core.eventbus.Message[String]) => {
        println("Processing info request...")

        val id: String = message.body()

        if (id.isEmpty) error("id.isEmpty")
        else {
          //Either[Int, Map[String, Seq[String]]]
          pdfSrcServer.getDocumentInfo(id) match {
            case Right(inf) =>
              val inf2: Seq[(String, Json)] =
                inf.map(kv => kv._1 -> Json.fromValues(kv._2.map(str => Json.fromString(str))) ).toSeq
              val json: Json = Json.obj(inf2: _*)
              info(s"Document deleted: id=$id")
              message.reply(json.spaces2)
            case Left(err) =>
              error(s"Document info: code=$err id=$id")
              message.reply("**")
          }
        }
      }
    )
  }
}
