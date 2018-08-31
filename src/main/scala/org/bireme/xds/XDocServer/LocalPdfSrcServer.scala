/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URL
import java.nio.charset.Charset

import io.circe._
import io.circe.parser._
import scalaj.http.Http

import scala.util.{Failure, Success, Try}

class LocalPdfSrcServer(docServer: SolrDocServer,
                        pdfDocServer : Either[URL, LocalPdfDocServer]) extends DocumentServerImpl(docServer) {

  val timeout: Int = 4 * 60 * 1000

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @return the original metadata document content if it is found/created or 404(not found) or 500(internal server error)
    */
  def getDocument2(id: String): Either[Int, Map[String,Set[String]]] = {
    docServer.getDocument(id).flatMap { is =>
      Tools.inputStream2Array(is) match {
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
    pdfDocServer match {
      case Right(localServer) =>
        val pdfis: Either[Int, InputStream] = localServer.getDocument(id) match {
          case Right(_) => Left(409)
          case Left(err) => err match {
            case 404 =>
              localServer.createDocument(id, url, info.map(_ + ("url" -> Seq(url)))) match {
                case 201 => localServer.getDocument(id)
                case err2 => Left(err2)
              }
            case 500 => Left(500)
          }
        }
        pdfis match {
          case Right(is2) =>
            val is3 = new ByteArrayInputStream(Tools.inputStream2Array(is2).get)
            is3.mark(Integer.MAX_VALUE)
            val info2 = Some(createDocumentInfo(id, Some(is3), info) + ("url" -> Seq(url)))
            is3.reset()
            docServer.createDocument(id, is3, info2)
          case Left(err) => err
        }
      case Left(remoteServer) =>
        val url1: String = remoteServer.getProtocol+ "://" + remoteServer.getHost + ":" + remoteServer.getPort + "/getDocument"
        val params: Seq[(String, String)] = (info.getOrElse(Map[String,Seq[String]]()) ++ Seq("id" -> Seq(id), "url" -> Seq(url)))
          .foldLeft(Seq[(String,String)]()) {
            case (seq, kv) =>
              val aux: Seq[(String, String)] = kv._2.foldLeft(Seq[(String,String)]()) {
                case (seq2, selem) => seq2 :+ (kv._1 -> selem)
              }
              seq ++: aux
          }
        Try(Http(url1).params(params).timeout(timeout, timeout).asBytes) match {
          case Success(response) =>
            if (response.is2xx) {
              val is2 = new ByteArrayInputStream(response.body)
              is2.mark(Integer.MAX_VALUE)
              val info2 = Some(createDocumentInfo(id, Some(is2), info) + ("url" -> Seq(url)))
              is2.reset()
              docServer.createDocument(id, is2, info2)
            }
            else if (response.is4xx) 404
            else 500
          case Failure(_) => 500
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
    pdfDocServer match {
      case Right(localServer) =>   // Local server
        val pdfis: Either[Int, InputStream] = localServer.getDocument(id) match {
          case Right(is) => Right(is)
          case Left(err) => err match {
            case 404 =>
              localServer.createDocument(id, source, info) match {
                case 201 => localServer.getDocument(id)
                case err2 => Left(err2)
              }
          }
        }
        pdfis match {
          case Right(is2) =>
            val is3 = new ByteArrayInputStream(Tools.inputStream2Array(is2).get)
            is3.mark(Integer.MAX_VALUE)
            val info2 = Some(createDocumentInfo(id, Some(is3), info))
            is3.reset()
            docServer.createDocument(id, is3, info2)
          case Left(err) => err
        }
      case Left(remoteServer) =>  // Remote server
        val url1: String = remoteServer.getProtocol+ "://" + remoteServer.getHost + ":" + remoteServer.getPort + "/putDocument"
        val params: Seq[(String, String)] = (info.getOrElse(Map[String,Set[String]]()) ++ Seq("id" -> Set(id)))
          .foldLeft(Seq[(String,String)]()) {
            case (seq, kv) =>
              val aux: Seq[(String, String)] = kv._2.foldLeft(Seq[(String,String)]()) {
                case (seq2, selem) => seq2 :+ (kv._1 -> selem)
              }
              seq ++: aux
          }
        Tools.inputStream2Array(source) match {
          case Some(array) =>
            Try(Http(url1).params(params).timeout(timeout, timeout).postData(array).asBytes) match {
              case Success(response) =>
                if (response.is2xx) {
                  val is2 = new ByteArrayInputStream(response.body)
                  is2.mark(Integer.MAX_VALUE)
                  val info2 = Some(createDocumentInfo(id, Some(is2), info))
                  is2.reset()
                  docServer.createDocument(id, is2, info2)
                }
                else if (response.is4xx) 404
                else 500
              case Failure(_) => 500
            }
          case None => 500
        }
    }
  }

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
                                  info: Option[Map[String, Seq[String]]] = None): Map[String, Seq[String]] =
    docServer.getDocumentInfo(id) match {
      case Right(map) => map ++ info.getOrElse(Map[String, Seq[String]]())
      case Left(_) => super.createDocumentInfo(id, source, info)
    }
}
