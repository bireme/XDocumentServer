/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer
import java.io.InputStream
import java.net.URL
import com.roundeights.hasher.{Algo, InputStreamTap}

/**
  *
  * @param docServer a document server to store/retrieve the thumbnails
  * @param pdfDocServer a remote or local pdf document server to retrieve the pdf documents
  */
class LocalThumbnailServer(docServer: DocumentServer,
                           pdfDocServer : Option[Either[URL, LocalPdfDocServer]]) {
  val implement = new DocumentServerImpl(docServer)
  val mtRecognizer = new MediaTypeRecognizer()

  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  def getDocuments: Set[String] = implement.getDocuments

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document mcontent
    * @param mType the type of the document
    * @param info metainfo of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  def createDocument(id: String,
                     source: InputStream,
                     mType: MediaType,
                     info: Option[Map[String, Set[String]]] = None): Int = {
    val stream: InputStreamTap = Algo.sha1.tap(source) // Reuse inputstream to calculate hash and generate the thumbnail

    val isOpt: Option[InputStream] = mType match {
      case Pdf =>
        myPDFToImage.convert(stream)
      case _ => Some(stream)
    }

    isOpt match {
      case Some(is) =>
        val tuple: (String, Set[String]) = "hash" -> Set[String](stream.hash)
        val ret: Int = info match {
          case Some(_) => implement.createDocument(id, is, info.map(_ + tuple))
          case None =>
            if (is.markSupported()) {
              is.mark(Integer.MAX_VALUE)
              val info2: Map[String, Set[String]] = implement.createDocumentInfo(id, Some(is)) + tuple
              is.reset()
              implement.createDocument(id, is, Some(info2))
            } else implement.createDocument(id, is, Some(implement.createDocumentInfo(id, None) + tuple))
        }
        is.close()
        ret
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
  def createDocument(id: String,
                     url: String,
                     info: Option[Map[String, Set[String]]]): Int = {
  mtRecognizer.getMediaType(url) match {
    case Right(mtype) =>
      mtype match {
        case Pdf =>
          Tools.url2InputStream(url) match {
            case Some(is) =>
              val ret = createDocument(id, is, mtype, info)
              is.close()
              ret
            case None => 500
          }
        case Video =>
          getVideoThumb(url) match {
            case Right(is) =>
              val ret = createDocument(id, is, mtype, info)
              is.close()
              ret
            case Left(_) => 500
          }
        case Image =>
          Tools.url2InputStream(url) match {
            case Some(is) =>
              val ret = createDocument(id, is, mtype, info)
              is.close()
              ret
            case None => 500
            case _ => 500
          }
        case _ => 500
      }
      case Left(_) => 500
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
              case _ => createDocument2(id, url1)
            }
          case Right(map) => // There is a previous document stored
            val sUrl = map.getOrElse("url", Set("")).head
            if (url1.trim.isEmpty || url1.trim.equals(sUrl)) implement.getDocument(id)  // The url new and stored are the same, so retrieve the stored version
            else {  // Store the new version because urls are different
              deleteDocument(id)
              createDocument2(id, url1)
            }
        }
      case None => implement.getDocument(id) // There is no new url version, so retrieve the stored version
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @return the original/new document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  private def createDocument2(id: String,
                              url: String): Either[Int, InputStream] = {
    if (url.trim.isEmpty) Left(404)
    else {
      mtRecognizer.getMediaType(url) match {
        case Right(mType) =>
          mType match {
            case Pdf =>
              getPdfDocument(id, url) match {
                case Right(is) =>
                  implement.createDocument(id, is, Some(Map("url" -> Set[String](url)))) match {
                    case 201 => implement.getDocument(id)
                    case err => Left(err)
                  }
                case Left(err) => Left(err)
              }
            case Video =>
              getVideoThumb(url) match {
                case Right(is) =>
                  implement.createDocument(id, is, Some(Map("url" -> Set[String](url)))) match {
                    case 201 => implement.getDocument(id)
                    case err => Left(err)
                  }
                case Left(err) => Left(err)
              }
            case _ => Left(500)
          }
        case Left(_) => Left(500)
      }
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source the source of the document content
    * @param mType the type of the document
    * @param info metainfo of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  def replaceDocument(id: String,
                      source: InputStream,
                      mType: MediaType,
                      info: Option[Map[String, Set[String]]] = None): Int = {
    deleteDocument(id) match {
      case 500 => 500
      case _ => createDocument(id, source, mType, info)
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
                      info: Option[Map[String, Set[String]]]): Int = {
    deleteDocument(id) match {
      case 500 => 500
      case _ => createDocument(id, url, info)
    }
  }

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  def deleteDocument(id: String): Int = implement.deleteDocument(id)

  /**
    * Delete all stored documents
    * @return a http error code. 200 (ok) or or 500 (internal server error)
    */
  def deleteDocuments(): Int = implement.deleteDocuments()

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  def getDocumentInfo(id: String): Either[Int, Map[String, Set[String]]] = implement.getDocumentInfo(id)

  /**
    * Retrieve a stored pdf document
    * @param id document identifier
    * @param url the location where the document is
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  private def getPdfDocument(id: String,
                             url: String): Either[Int, InputStream] = {
    pdfDocServer match {
      case Some(pdser) =>
        pdser match {
          case Left(remoteUrl: URL) => getRemotePdf(remoteUrl, id, url)
          case Right(lpds: LocalPdfDocServer) => lpds.getDocument(id, Some(url))
        }
      case None => Left(500)
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
                           url: String): Either[Int, InputStream] = {
    val url1: URL = new URL(s"${remoteUrl.getProtocol}://${remoteUrl.getHost}:${remoteUrl.getPort}" +
        s"/getDocument?id=$id&url=$url")

    Tools.url2InputStream(url1.toString) match {
      case Some(is) => Right(is)
      case None => Left(400)
    }
  }

  /**
    * Retrieve the thumbnail associated to a video
    * @param url the video url
    * @return the thumbnail input stream
    */
  private def getVideoThumb(url: String): Either[Int, InputStream] = {
    url.trim match {
      case "" => Left(500)
      case urlT =>  //https://www.youtube.com/watch?v=Bf_YemfEaDs
        "https://www.youtube.com/watch\\?v=([^\\&]+)".r.findFirstMatchIn(urlT).map {
          mat =>
            val thumbUrl: String = s"https://img.youtube.com/vi/${mat.group(1)}/sddefault.jpg"
            Tools.url2InputStream(thumbUrl) match {
              case Some(is) => Right(is)
              case None => Left(500)
            }
        }.getOrElse(Left(500))
    }
  }
}