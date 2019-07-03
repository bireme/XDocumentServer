/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.EventBus
import wvlet.log.LogSupport

class PdfUpdVerticle(pdfDocDir: String,
                     solrColUrl: Option[String],
                     thumbDir: Option[String]) extends ScalaVerticle with LogSupport {
  override def start(): Unit = {
    val eb: EventBus = vertx.eventBus()
    println("PdfUpdVerticle is now running ...")

    eb.consumer("org.bireme.xds.UpdateDocuments.upd",
      (message: io.vertx.scala.core.eventbus.Message[String]) => {
        val id = message.body()
        println(s"Processing update request id=$id")

        if (solrColUrl.isDefined && thumbDir.isDefined) {
          val updDocs = new UpdateDocuments(pdfDocDir, solrColUrl.get, thumbDir.get, None)
          if (!updDocs.updateOne(id)) {
            error(s"error in updating document id=$id")
          }
        }
      }
    )
  }
}
