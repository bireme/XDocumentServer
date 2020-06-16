/*=========================================================================

   XDocumentServer © Pan American Health Organization, 2018.
   See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

 ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{BufferedWriter, File}
import java.nio.charset.Charset
import java.nio.file.Files

import io.circe.{ACursor, Json}
import io.circe.parser.parse

import scala.io.{BufferedSource, Source}
import scala.language.postfixOps
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import sys.process._

/**
  *
  * Check the urls of all documents having community/collection using the Linux tool 'curl'
  *
  * author: Heitor Barbieri
  * date: 20191212
  */
object CheckCollectionUrls extends App {
  private def usage(): Unit = {
    System.err.println("usage: CheckCollectionUrls <outputFile>")
    System.exit(1)
  }

  if (args.length < 1) usage()

  Tools.disableCertificateValidation()

  val fiadminApi: String = "https://fi-admin.bvsalud.org/api"
  val writer: BufferedWriter = Files.newBufferedWriter(new File(args(0)).toPath, Charset.forName("utf-8"))

  println("communityId|collectionId|documentId|url|errCode|errMess")
  writer.write("\ncommunityId|collectionId|documentId|url|errCode|errMess")

  getCommunityIds match {
    case Right(comIds) =>
      comIds.sorted.foreach {
        comId =>
          getCollectionMeta(comId) match {
            case Right(seq) =>
              seq foreach {
                case (colId, _, _) =>
                  getDocUrls(colId) match {
                    case Right(seq2) =>
                      seq2 foreach {
                        case (docId, urls) =>
                          urls.foreach {
                            url =>
                              checkCurlUrl(url) match {
                                case (code, mess) =>
                                  println(s"$comId|$colId|$docId|$url|$code|$mess")
                                  System.out.flush()
                                  writer.write(s"\n$comId|$colId|$docId|$url|$code|$mess")
                              }
                          }
                      }
                    case Left(errMess) =>
                      println(errMess)
                      writer.write(s"\n$errMess")
                  }
              }
            case Left(msg) =>
              println(s"ERROR: $msg")
              writer.write(s"\n$msg")
          }
      }
    case Left(comMess) =>
      println(comMess)
      writer.write(s"\n$comMess")
  }
  writer.close()

  /**
    * Get the ids of all communities stored in FI-Admin
    * @return the communities ids or an error message
    */
  private def getCommunityIds: Either[String, Seq[String]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/community/?limit=0&format=json", "utf-8")
      val content = src.getLines().mkString("\n")
      src.close()

      parse(content)
    } match {
      case Success(json) => json match {
        case Right(js: Json) => Right(getComId(js.hcursor.downField("objects").downArray))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) => Left(ex.toString)
    }
  }

  /**
    * Get the ids of all communities stored in FI-Admin
    * @param elem a json document cursor
    * @return the communities ids
    */
  private def getComId(elem: ACursor): Seq[String] = {
    if (elem.succeeded) {
      val id = parseId(elem).getOrElse("")
      if (id.isEmpty) getComId(elem.right)
      else Seq(id) ++ getComId(elem.right)
    } else Seq()
  }

  /**
    * Get the metadata of the collections belonging to a community
    * @param communityId the community identifier
    * @return a sequence of collection metadata (id, name, community) or an error message
    */
  private def getCollectionMeta(communityId: String): Either[String, Seq[(String, String, String)]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/collection/?community=$communityId&limit=0&format=json", "utf-8")
      val content = src.getLines().mkString("\n")
      src.close()

      parse(content)
    } match {
      case Success(json) => json match {
        case Right(js: Json) => Right(getColMeta(js.hcursor.downField("objects").downArray))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) => Left(ex.toString)
    }
  }

  /**
    * Get the metadata of the collections belonging to a community
    * @param elem a json document cursor
    * @return a sequence of collection metadata (id, name, community)
    */
  private def getColMeta(elem: ACursor): Seq[(String, String, String)] = {
    if (elem.succeeded) {
      val id: String = parseId(elem).getOrElse("")
      val name: String = parseElem("name", elem).getOrElse("")
      val community: String = parseElem("parent", elem).getOrElse("")

      Seq((id, name, community)) ++ getColMeta(elem.right)
    } else Seq()
  }

  /**
    * Get the id of a collection or document
    * @param elem a json document cursor
    * @return the collection or document id
    */
  private def parseId(elem: ACursor): Option[String] = {
    elem.downField("id").as[Int] match {
      case Right(id: Int) => Some(id.toString)
      case Left(_) => None
    }
  }

  /**
    * Get the content of an element of the json document
    *
    * @param name the element name
    * @param elem a json document cursor
    * @return the content of the element
    */
  private def parseElem(name: String,
                        elem: ACursor): Option[String] = {
    elem.downField(name).as[String] match {
      case Right(value) => Some(value)
      case _ => None
    }
  }

  /**
    * Get the urls from all documents of a collection
    * @param colId the collection id
    * @return a sequence of (docid, urls) or an error message
    */
  private def getDocUrls(colId: String): Either[String, Seq[(String, Seq[String])]] = {
    Try {
      val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&limit=0&format=json", "utf-8")
      val content = src.getLines().mkString("\n")
      src.close()

      parse(content)
    } match {
      case Success(json) => json match {
        case Right(js: Json) => Right(getDocMeta(js.hcursor.downField("objects").downArray))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) => Left(ex.toString)
    }
  }

  /**
    * Get the document metadata
    * @param elem a json document cursor
    * @return a sequence of (document id, urls) or an error message
    */
  private def getDocMeta(elem: ACursor): Seq[(String, Seq[String])] = {
    if (elem.succeeded) {
      val id: String = parseId(elem).getOrElse("")
      val urls: Seq[String] = parseUrls(elem.downField("electronic_address").downArray)

      Seq((id, urls)) ++ getDocMeta(elem.right)
    } else Seq()
  }

  /**
    * Get all urls from a document
    * @param elem a json document cursor
    * @return a sequence of document urls
    */
  private def parseUrls(elem: ACursor): Seq[String] = {
    if (elem.succeeded) {
      val url: String = parseElem("_u", elem).getOrElse("")

      Seq(url) ++ parseUrls(elem.right)
    } else Seq()
  }

  /**
    * Check a given url using the Linux tool 'curl'
    * @param url the url to be checked
    * @return the (error code, error message)
    */
  private def checkCurlUrl(url: String): (Int, String) = {
    Try {
      // -L follow redirects
      // -I fetch the headers only
      // -k disable certificate check
      // -s hide extra outputs

      s"curl -LIks $url" !!
    } match {
      case Success(msg) =>
        Try {
          url.trim.substring(0,3) match {
            case "htt" => parseHttpMessage(msg)
            case "ftp" => parseFtpMessage(msg)
            case _ => throw new Exception("Illegal protocol")
          }
        } match {
          case Success(opt) => opt.getOrElse((500, s"[$url] invalid header"))
          case Failure(exc) => (500, s"[$url] ${exc.getMessage}")
        }
      case Failure(exc) => (500, s"[$url] ${exc.getMessage}")
    }
  }

  /**
    * Given the header of the url http response take the error code and error message
    * @param msg the url message returned from the curl call
    * @return (error code, error message)
    */
  private def parseHttpMessage(msg: String): Option[(Int, String)] = {
    val regex: Regex = "([^ ]+) +([^ ]+)( +(.+))?".r

    msg.split("\n").headOption.flatMap {
      line =>
        regex.findFirstMatchIn(line).map {
          mat =>
            val code = mat.group(2).toInt
            val msg = Option(mat.group(4)).getOrElse("")
            (code, msg)
        }
    }
  }

  /**
    * Given the header of the url ftp response build the error code and error message
    * @param msg the url message returned from the curl call
    * @return (error code, error message)
    */
  private def parseFtpMessage(msg: String): Option[(Int, String)] = {
    val regex: Regex = "Content-Length: (\\d+)".r

    regex.findFirstMatchIn(msg).map(_.group(1)).flatMap {
      len => if (len.toInt > 0) Some((200, "OK"))
             else None
    }
  }
}
