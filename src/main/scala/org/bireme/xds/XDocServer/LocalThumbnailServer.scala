/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.InputStream
import java.net.URL

class LocalThumbnailServer(docServer: DocumentServer,
                           pdfDocServer : Either[URL, LocalPdfDocServer]) extends DocumentServerImpl(docServer) {

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
    myPDFToImage.convert(source) match {
      case Some(is) =>
        info match {
          case Some(_) => super.createDocument(id, is, info)
          case None =>
            if (is.markSupported()) {
              is.mark(Integer.MAX_VALUE)
              val info2 = createDocumentInfo(id, Some(is))
              is.reset()
              super.createDocument(id, is, Some(info2))
            } else super.createDocument(id, is, Some(createDocumentInfo(id, None)))
        }
      case None => 500
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              url: String,
                              info: Option[Map[String, Seq[String]]]): Int = {
    Tools.url2InputStream(new URL(url)) match {
      case Some(is) =>
        info match {
          case Some(_) => super.createDocument(id, is, info.map(_ + ("url" -> Seq(url))))
          case None =>
            if (is.markSupported()) {
              is.mark(Integer.MAX_VALUE)
              val info2 = createDocumentInfo(id, Some(is))
              is.reset()
              super.createDocument(id, is, Some(info2))
            } else {
              super.createDocument(id, is, Some(createDocumentInfo(id, None, Some(Map("url" -> Seq(url))))))
            }
        }
      case None => 500
    }
  }

  /**
    * Retrieve a stored document if the url is the same of the stored one or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @return the original/new document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  def getDocument(id: String,
                  url: Option[String]): Either[Int, InputStream] = {
    url match {
      case Some(url1) =>
        getDocumentInfo(id) match {
          case Left(err) =>  // There is no previous document stored
            err match {
              case 500 => Left(500)
              case _ => createDocument2(id, url)
            }
          case Right(map) => // There is a previous document stored
            val sUrl = map.getOrElse("url", Seq("")).head
            if (url1.trim.isEmpty || url1.trim.equals(sUrl)) getDocument(id)  // The url new and stored are the same, so retrieve the stored version
            else {  // Store the new version because urls are different
              deleteDocument(id)
              createDocument2(id, url)
            }
        }
      case None => getDocument(id) // There is no new url version, so retrieve the stored version
    }
  }

  private def createDocument2(id: String,
                              url: Option[String]): Either[Int, InputStream] = {
    if (url.isDefined && url.get.trim.isEmpty) Left(404)
    else {
      getPdfDocument(id, url) match {
        case Right(is) =>
          createDocument(id, is, Some(Map("url" -> Seq[String](url.getOrElse(""))))) match {
            case 201 => getDocument(id)
            case err => Left(err)
          }
        case Left(err) => Left(err)
      }
    }
  }

  /**
    * Retrieve a stored pdf document
    * @param id document identifier
    * @param url the location where the document is
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  private def getPdfDocument(id: String,
                             url: Option[String]): Either[Int, InputStream] = {
    pdfDocServer match {
      case Left(remoteUrl: URL) => getRemotePdf(remoteUrl, id, url)
      case Right(lpds: LocalPdfDocServer) => lpds.getDocument(id, url)
    }
  }

  /**
    * Retrieve a remote stored document
    * @param remoteUrl the remote document location
    * @param id document identifier
    * @param url the location where the document is
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  private def getRemotePdf(remoteUrl: URL,
                           id: String,
                           url: Option[String]): Either[Int, InputStream] = {

    val url1 = url.getOrElse("")
    val url2 = new URL(s"${remoteUrl.getProtocol}://${remoteUrl.getHost}:${remoteUrl.getPort}" +
        s"/getDocument?id=$id&url=$url1")

    Tools.url2InputStream(url2) match {
      case Some(is) => Right(is)
      case None => Left(400)
    }
  }
}