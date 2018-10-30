/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import org.apache.pdfbox.pdmodel.{PDDocument, PDDocumentInformation}

import scala.util.{Failure, Success, Try}

class LocalPdfDocServer(docServer: DocumentServer) extends DocumentServerImpl(docServer) {

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  def getDocument(id: String,
                  url: Option[String],
                  info: Option[Map[String, Seq[String]]] = None): Either[Int, InputStream] = {
    url match {
      case Some(url1) =>
        getDocumentInfo(id) match {
          case Left(err) =>  // There is no previous document stored
            err match {
              case 500 => Left(500)
              case _ => createDocument2(id, url1, info)
            }
          case Right(map) => // There is a previous document stored
            val sUrl = map.getOrElse("url", Seq("")).head
            if (url1.trim.isEmpty || url1.trim.equals(sUrl)) getDocument(id)  // The url new and stored are the same, so retrieve the stored version
            else createDocument2(id, url1, info, deleteBefore = true)// Store the new version because urls are different
        }
      case None => getDocument(id) // There is no new url version, so retrieve the stored version
    }
  }

  private def createDocument2(id: String,
                              url: String,
                              info: Option[Map[String, Seq[String]]],
                              deleteBefore: Boolean = false): Either[Int, InputStream] = {
    if (url.trim.isEmpty) Left(404)
    else {
      val status = if (deleteBefore) deleteDocument(id) else 200

      status match {
        case 200 =>
          createDocument(id, url, info) match {
            case 201 => getDocument(id)
            case err => Left(err)
          }
        case err => Left(err)
      }
    }
  }

  /**
    * Create a metadata for the document
    * @param id document identifier
    * @param source document input stream
    * @param info other metadata source
    * @return the document metadata
    */
  override def createDocumentInfo(id: String,
                                  source: Option[InputStream] = None,
                                  info: Option[Map[String, Seq[String]]] = None): Map[String, Seq[String]] = {
    val now: Date = Calendar.getInstance().getTime
    val dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
    val date: String = dateFormat.format(now)
    val infoT: Option[Map[String, Seq[String]]] =
      info.map(_.map(kv => kv._1.trim() -> kv._2.map(_.replace("\n", " ").trim()) ))
    val map = Map(
      "id" -> Seq(id),
      "date" -> Seq(date)) ++ infoT.getOrElse(Map[String, Seq[String]]())

    Try {
      if (source.isEmpty) throw new NullPointerException()
      val pddoc: PDDocument = PDDocument.load(source.get)
      val info2: PDDocumentInformation = pddoc.getDocumentInformation
      val map2: Map[String, Seq[String]] = Map(
        "title" -> (if (info2.getTitle == null) null else Seq(info2.getTitle)),
        "subject" -> (if (info2.getSubject == null) null else Seq(info2.getSubject)),
        "authors" -> (if (info2.getAuthor == null) null else Seq(info2.getAuthor)),
        "keywords" -> (if (info2.getKeywords == null) null else Seq(info2.getKeywords))
      ).filter { case (_, v) => v != null }

      pddoc.close()
      map2
    } match {
      case Success(mp) => map ++ mp
      case Failure(_) => map
    }
  }
}