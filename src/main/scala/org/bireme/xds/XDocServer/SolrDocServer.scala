/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, File, InputStream}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import io.circe.parser.parse
import io.circe.{ACursor, HCursor, Json}
import org.apache.commons.io.FileUtils
import scalaj.http.{Http, HttpResponse}

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class SolrDocServer(url: String) extends DocumentServer {
  val urlT: String = url.trim
  val url1: String = if (urlT.endsWith("/")) urlT else urlT + "/"

  val mainFields: Set[String] = Set("id",      // identificador,
                                    "_text_")  // texto do documento

  val metadataFields: Set[String] =
    Set("id",            // identificador
        "ti",            // título
        "type",          // tipo de documento
        "au",            // autor
        "da",            // ano
        "kw",            // palavras chave
        "fo",            // fonte
        "ab",            // resumo
        "ur",            // url
        "la",            // idioma
        "mh",            // descritores
        "com",           // comunidade
        "col",           // coleção
        "ud",            // data de atualização
        "tu")            // url do thumbnail
  val timeout: Int = 4 * 60 * 1000

  /**
    * List the : $xids of all pdf documents
    *
    * @return a set having all pdf document ids
    */
  override def getDocuments: Set[String] = {
    val regex: Regex = "\"id\"\\:\"([^\"]+)\"".r

    Try(Http(url1 + "select").params(Map("fl" -> "id", "q" -> "*:*", "rows" -> "10000")).timeout(timeout, timeout).asString) match {
      case Success(response) =>
        if (response.is2xx) {
          regex.findAllMatchIn(response.body).foldLeft(Set[String]()) {
            case (set, rmatch) => set + rmatch.group(1)
          }
        } else Set[String]()
      case Failure(_) => Set[String]()
    }
  }

  /**
    * Retrieve a stored document
    *
    * @param id document identifier
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  override def getDocument(id: String): Either[Int, InputStream] = {
    val regex: Regex = "\"response\"\\:\\{\"numFound\"\\:(\\d)".r

    Try(Http(url1 + "query").param("q", s"id:$id").timeout(timeout, timeout).asString) match {
      case Success(response) =>
        if (response.is2xx) {
          regex.findFirstMatchIn(response.body) match {
            case Some(rmatch) =>
              if (rmatch.group(1).toInt == 0) Left(404)
              else Right(new ByteArrayInputStream(response.body.getBytes("utf-8")))
            case None => Left(500)
          }
        }
        else if(response.is4xx) Left(404)
        else Left(500)
      case Failure(_) => Left(500)
    }
  }

  /**
    * Store a new document
    *
    * @param id     document identifier
    * @param source the source of the document content
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              source: InputStream,
                              info: Option[Map[String, Set[String]]]): Int = {
    //curl "http://localhost:8983/solr/teste1/update/extract?literal.id=doc6&defaultField=text&commit=true" --data-binary @/home/heitor/Downloads/shapeless-guide.pdf -H 'Content-type:application/pdf'

    //println(s"+++ indexing document id=$id")

    val info2: Option[Map[String, Set[String]]] = info.map(_ + ("id" -> Set(id)))
    val parameters: List[(String, String)] = (info2 match {
      case Some(param) => param.foldLeft(List[(String,String)]()) {
        case (lst, (k,seq)) => seq.foldLeft(lst) {
          case (lst2, elem) => lst2 :+ (k -> elem)
        }
      }
      case None => List[(String,String)]()
    }).map { case (x,y) => "literal." + x.trim -> y.trim } ++ List("defaultField" -> "_text_", "commit" -> "true")

    getDocument(id) match {
      case Right(is) =>
        is.close()
        409
      case Left(500) => 500
      case _ => // 404 (not found)
        Tools.inputStream2Array(source) match {
          case Some(arr) =>
            //println(s"arr size=${arr.length}")
            if (arr.length == 0) 500
            else {
              Try(
                Http(url1 + "update/extract")
                  .header("Content-type", "application/pdf")
                  .timeout(timeout, timeout)
                  .params(parameters)
                  .postData(arr)
                  .asString
              ) match {
                case Success(response: HttpResponse[String]) =>
                  //println(s"response=${response.body}")
                  response.code match {
                    case 200 => 201
                    case _   => 500
                  }
                case Failure(exception) =>
                  println(s"exception=$exception")
                  500
              }
            }
          case None => 500
        }
    }
  }

  /**
    * Store a new document
    *
    * @param id     document identifier
    * @param url the location where the document is
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              url: String,
                              info: Option[Map[String, Set[String]]]): Int = {
    //curl "http://localhost:8983/solr/teste1/update/extract?literal.id=doc6&defaultField=text&commit=true" --data-binary @/home/heitor/Downloads/shapeless-guide.pdf -H 'Content-type:application/pdf'

    //println(s"+++ indexing document id=$id")

    val info2: Option[Map[String, Set[String]]] = info.map(_ + ("id" -> Set(id)))
    val parameters: List[(String, String)] = (info2 match {
      case Some(param) => param.foldLeft(List[(String,String)]()) {
        case (lst, (k,seq)) => seq.foldLeft(lst) {
          case (lst2, elem) => lst2 :+ (k -> elem)
        }
      }
      case None => List[(String,String)]()
    }).map { case (x,y) => "literal." + x.trim -> y.trim } ++ List("defaultField" -> "_text_", "commit" -> "true")

    getDocument(id) match {
      case Right(is) =>
        is.close()
        409
      case Left(500) => 500
      case _ => // 404 (not found)
        Tools.url2ByteArray(url) match {
          case Some(arr) =>
            //println(s"arr size=${arr.size}")

            Try(Http(url1 + "update/extract").header("Content-type", "application/pdf").timeout(timeout, timeout)
              .params(parameters).postData(arr).asString) match {
              case Success(response: HttpResponse[String]) =>
                //println(s"response=${response.body}")
                response.code match {
                  case 200 => 201
                  case _   => 500
                }
              case Failure(exception) =>
                println(s"exception=$exception")
                500
            }
          case None => 500
        }
    }
  }

  /**+
    * Replace a stored document if there is some or create a new one otherwise
    *
    * @param id     document identifier
    * @param source source of the document content
    * @param info   metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               source: InputStream,
                               info: Option[Map[String, Set[String]]] = None): Int = {
    val del: Int = deleteDocument(id)

    if (del == 500) 500
    else if (createDocument(id, source, info) == 500) 500
    else if (del == 200) 200 else 201
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
    val del: Int = deleteDocument(id)

    if (del == 500) 500
    else if (createDocument(id, url, info) == 500) 500
    else if (del == 200) 200 else 201
  }

  /**
    * Delete a stored document
    *
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  override def deleteDocument(id: String): Int = {

    //curl http://localhost:8983/solr/teste3/update -H "Content-type: text/xml" --data-binary '<delete><query>id:978-85-334-1939-1</query></delete>'
    //curl http://localhost:8983/solr/teste3/update -H "Content-type: text/xml" --data-binary '<commit/>'

    //val response1 = Http("http://localhost:9000/solr/teste3/config")
    Try(Http(url1 + "update").headers(Seq("Content-type" -> "text/xml", "Accept"-> "*/*"))
      .timeout(timeout, timeout)
      .postData(s"<delete><query>id:$id</query></delete>").asString) match {
      case Success(response: HttpResponse[String]) =>
        response.code match {
          case 200 =>
            Try(Http(url1 + "update").header("Content-type", "text/xml").timeout(timeout, timeout)
              .postData("<commit/>").asString) match {
                case Success(response: HttpResponse[String]) =>
                  response.code match {
                    case 200 => 200
                    case _ => 500
                  }
                case Failure(exception) =>
                  println(s"exception=$exception")
                  500
              }
          case 404 => 404
          case _ => 500
        }
      case Failure(exception) =>
        println(s"exception=$exception")
        500
    }
  }

  /**
    * Delete all stored documents
    * @return a http error code. 200 (ok) or or 500 (internal server error)
    */
  def deleteDocuments(): Int = {
    //http://host:port/solr/[core name]/update?stream.body=<delete><query>*:*</query></delete>&commit=true
    Try(Http(url1 + "update").headers(Seq("Content-type" -> "text/xml", "Accept"-> "*/*"))
      .timeout(timeout, timeout)
      .postData("<delete><query>*:*</query></delete>").asString) match {
      case Success(response: HttpResponse[String]) =>
        response.code match {
          case 200 =>
            Try(Http(url1 + "update").header("Content-type", "text/xml").timeout(timeout, timeout)
              .postData("<commit/>").asString) match {
              case Success(response: HttpResponse[String]) =>
                response.code match {
                  case 200 => 200
                  case _ => 500
                }
              case Failure(exception) =>
                println(s"exception=$exception")
                500
            }
          case _ => 500
        }
      case Failure(exception) =>
        println(s"exception=$exception")
        500
    }
  }

  /**
    * Retrieve metadata of a stored pdf document
    *
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  override def getDocumentInfo(id: String): Either[Int, Map[String, Set[String]]] = {
    // "http://localhost:8983/solr/teste3/select?q=id:978-85-334-1911-7"

    Try(Http(url1 + "query").timeout(timeout, timeout).param("q", s"id:$id").asString) match {
      case Success(response: HttpResponse[String]) =>
        if (response.is2xx) {
          val json: Json = parse(response.body).getOrElse(Json.Null)
          val cursor: HCursor = json.hcursor
          val doc: ACursor = cursor.downField("response").downField("docs").downArray

          doc.keys match {
            case Some(it: Iterable[String]) =>
              Right(it.foldLeft(Map[String, Set[String]]()) {
                case (mp, key: String) =>
                  if (metadataFields.contains(key)) {
                    val elem = doc.downField(key)
                    elem.as[Array[String]] match {
                      case Right(arr) => mp + (key -> arr.toSet)
                      case Left(_) => elem.as[String] match {
                        case Right(str) => mp + (key -> Set(str))
                        case Left(_) => mp
                      }
                    }
                  } else mp
              })
            case _ => Left(500)
          }
        } else Left(500)
      case Failure(_) => Left(500)
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
                                  source: Option[InputStream],
                                  info: Option[Map[String, Set[String]]] = None): Map[String, Set[String]] = {
    val now: Date = Calendar.getInstance().getTime
    val dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
    val date: String = dateFormat.format(now)

    Map("id" -> Set(id), "updated_date" -> Set(date)) ++ info.getOrElse(Map[String, Set[String]]())
  }

  private def getSolrDir: Option[String] = {
    val url2 = url1.substring(0, url1.length - 1)
    val lastSlash = url2.lastIndexOf('/')
    val base = url2.substring(0, lastSlash)
    val regex = "\"-Dsolr.solr.home=([^\"]+)\"".r

    Try(Http(s"$base/admin/info/system")
      .timeout(timeout, timeout).asString) match {
      case Success(response) =>
        if (response.is2xx) {
          regex.findFirstMatchIn(response.body) map(_.group(1))
        } else None
      case Failure(_) => None
    }
  }

  /**
    * Create the collection
    *
    * @return 201 if it is created or 500 (internal server error)
    */
  def createCollection(): Int = {
    val url2 = url1.substring(0, url1.length - 1)
    val lastSlash = url2.lastIndexOf('/')
    val base = url2.substring(0, lastSlash)
    val collection = url2.substring(lastSlash + 1)
    val fromDir = "pdf-solr-conf"
    val toDir: Option[String] = getSolrDir.map(sd => s"$sd/$collection")
    val tDir = toDir.getOrElse("")

    if (toDir.isEmpty) 500
    else {
      Try[Unit] (FileUtils.copyDirectory(new File(fromDir), new File(s"$tDir/conf"))) match {
        case Success(_) =>
          Try(
            Http(s"$base/admin/cores?action=CREATE&name=$collection")
            .timeout(timeout, timeout).asString
          ) match {
            case Success(response) => if (response.is2xx) 201 else 500
            case Failure(_)        => 500
          }
        case Failure(_) => 500
      }
    }
  }

  /**
    * Delete a collection
    * @return 200 if it is deleted or 400 (not found) or 500 (internal server error)
    */
  def deleteCollection(): Int = {
    val url2 = url1.substring(0, url1.length - 1)
    val lastSlash = url2.lastIndexOf('/')
    val base = url2.substring(0, lastSlash)
    val collection = url2.substring(lastSlash + 1)
    val regex: Regex = "\"status\"\\:(\\d+)\\,".r

    Try(Http(s"$base/admin/cores?action=UNLOAD&core=$collection&deleteIndex=true&deleteDataDir=true&deleteInstanceDir=true")
      .timeout(timeout, timeout).asString) match {
      case Success(response) =>
        if (response.is2xx) {
          regex.findFirstMatchIn(response.body) match {
            case Some(rmatch) =>
              val ret = rmatch.group(1).toInt
              if (ret == 0) 200
              else if (ret >= 400 & ret <= 499) 400
              else 500
            case None => 500
          }
        } else 500
      case Failure(_) => 500
    }
  }
}
