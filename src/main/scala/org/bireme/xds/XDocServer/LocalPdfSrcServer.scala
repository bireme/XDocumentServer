/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.Charset

import io.circe._
import io.circe.parser._

class LocalPdfSrcServer(solrDocServer: SolrDocServer,
                        pdfDocServer : LocalPdfDocServer) extends DocumentServer {

  val timeout: Int = 4 * 60 * 1000

  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  def getDocuments: Set[String] = solrDocServer.getDocuments

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  override def getDocument(id: String): Either[Int, InputStream] = pdfDocServer.getDocument(id)

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @return the original metadata document content if it is found or 404(not found) or 500(internal server error)
    */
  def getDocument2(id: String): Either[Int, Map[String,Set[String]]] = {
    solrDocServer.getDocument(id).flatMap {
      is: InputStream =>
        val ret: Either[Int, Map[String, Set[String]]] = Tools.inputStream2Array(is) match {
          case Some(array) =>
            val str = new String(array, Charset.forName("utf-8"))
            val json: Json = parse(str).getOrElse(Json.Null)
            val cursor: HCursor = json.hcursor
            val docs: ACursor = cursor.downField("response").downField("docs")
            if (docs.failed) Left(404)
            else {
              val doc: ACursor = docs.downArray.first
              Right(doc.keys.get.map(key => (key,cursor.downField(key))).foldLeft(Map[String, Set[String]]()) {
                case (map, (key, acursor)) => acursor.focus match {
                  case Some(json2) =>
                    val set = map.getOrElse(key, Set[String]())
                    if (json2.isArray) map + (key -> (set + json2.asArray.get.head.toString))
                    else map + (key -> (set + json2.toString))
                  case None => map
                }
              })
            }
          case None => Left(500)
        }
        is.close()
        ret
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500(internal server error)
    */
  override def createDocument(id: String,
                              url: String,
                              info: Option[Map[String,Seq[String]]]): Int = {
    pdfDocServer.getDocument(id) match {
      case Right(_) => 409
      case Left(err) => err match {
        case 404 =>
          pdfDocServer.createDocument(id, url, info.map(_ + ("url" -> Seq(url)))) match {
            case 201 =>
              pdfDocServer.getDocument(id) match {
                case Right(is: InputStream) =>
                  val is2 = new ByteArrayInputStream(Tools.inputStream2Array(is).get)
                  is2.mark(Integer.MAX_VALUE)
                  val info2 = Some(createDocumentInfo(id, Some(is2), info) + ("url" -> Seq(url)))
                  is2.reset()
                  val ret: Int = solrDocServer.createDocument(id, is2, info2)
                  is2.close()
                  is.close()
                  ret
                case Left(err2) => err2
              }
            case err3 => err3
          }
        case 500 => 500
      }
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document content
    * @param info metainfo of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              source: InputStream,
                              info: Option[Map[String, Seq[String]]] = None): Int = {
    pdfDocServer.getDocument(id) match {
      case Right(_) => 409
      case Left(err) => err match {
        case 404 =>
          pdfDocServer.createDocument(id, source, info) match {
            case 201 =>
              pdfDocServer.getDocument(id) match {
                case Right(is) =>
                  val is2: ByteArrayInputStream = new ByteArrayInputStream(Tools.inputStream2Array(is).get)
                  is2.mark(Integer.MAX_VALUE)
                  val info2 = Some(createDocumentInfo(id, Some(is2), info))
                  is2.reset()
                  val ret = solrDocServer.createDocument(id, is2, info2)
                  is2.close()
                  is.close()
                  ret
                case Left(err2) => err2
              }
            case err3 => err3
          }
      }
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source source of the document content
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               source: InputStream,
                               info: Option[Map[String, Seq[String]]] = None): Int = {
    pdfDocServer.replaceDocument(id, source, info) match {
      case 500 => 500
      case _ =>
        if (source.markSupported()) {
          source.reset()
          solrDocServer.replaceDocument(id, source, info)
        } else 500
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  def replaceDocument(id: String,
                      url: String,
                      info: Option[Map[String, Seq[String]]]): Int = {
    pdfDocServer.replaceDocument(id, url, info) match {
      case 500 => 500
      case _ => solrDocServer.replaceDocument(id, url, info)
    }
  }

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  def deleteDocument(id: String): Int = {
    pdfDocServer.deleteDocument(id) match {
      case 200 => solrDocServer.deleteDocument(id)
      case err => err
    }
  }

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  def getDocumentInfo(id: String): Either[Int, Map[String, Seq[String]]] = solrDocServer.getDocumentInfo(id)

  /**
    * Create a metadata for the document
    *
    * @param id document identifier (document id from FI Admin)
    * @param source source of the document content
    * @param info other metadata source
    * @return the document metadata
    */
  override def createDocumentInfo(id: String,
                                  source: Option[InputStream] = None,
                                  info: Option[Map[String, Seq[String]]] = None): Map[String, Seq[String]] = {
    val map: Map[String, Seq[String]] = solrDocServer.createDocumentInfo(id, source, info)
    pdfDocServer.createDocumentInfo(id, source, Some(map))
    map
  }
}
