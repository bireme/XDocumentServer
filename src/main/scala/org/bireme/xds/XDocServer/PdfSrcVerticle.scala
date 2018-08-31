/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import io.vertx.lang.scala.ScalaVerticle
import wvlet.log.LogSupport

class PdfSrcVerticle(pdfSrcServer: LocalPdfSrcServer) extends ScalaVerticle with LogSupport {
  override def start(): Unit = {
    val eb = vertx.eventBus()
    println("PdfSrcVerticle is now running ...")

    eb.consumer(
      "org.bireme.xds.XDocServer.put",
      (message: io.vertx.scala.core.eventbus.Message[String]) => {
        println("Processing put request...")

        val mapParams: Map[String, Seq[String]] = Tools.string2Map(message.body())
        val id: Option[Seq[String]] = mapParams.get("id")
        val url: Option[Seq[String]] = mapParams.get("url")

        if (id.isEmpty || url.isEmpty) error("id.isEmpty || url.isEmpty")
        else {
          // Verify if the document is already stored
          pdfSrcServer.getDocument2(id.get.head) match {
            case Right(_) => // yes, then delete it and create it
              pdfSrcServer.deleteDocument(id.get.head) match {
                case 500 => error(s"Document deletion: code=500 id=${id.get.head} url=${url.get.head}")
                case _ =>
                  val ret = pdfSrcServer.createDocument(id.get.head, url.get.head, Some(mapParams))
                  if (ret == 201) info(s"Document created: id=${id.get} url=${url.get}")
                  else error(s"Document creation: code=$ret id=${id.get.head} url=${url.get.head} params=$mapParams")
              }
            case Left(value) =>
              value match {
                case 404 => // no, then create it
                  val ret = pdfSrcServer.createDocument(id.get.head, url.get.head, Some(mapParams))
                  if (ret == 201) info(s"Document created: id=${id.get} url=${url.get}")
                  else error(s"Document creation: code=$ret id=${id.get.head} url=${url.get.head} params=$mapParams")
                case err => error(s"Document deletion: code=$err id=${id.get.head} url=${url.get.head}")
              }
          }
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
  }
}
