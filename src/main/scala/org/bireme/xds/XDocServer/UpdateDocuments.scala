/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File

import io.circe._
import io.circe.parser._

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

//https://github.com/bireme/fi-admin/wiki/API

object UpdateDocuments extends App {

  private def usage(): Unit = {
    System.err.println("usage: UpdateDocuments " +
      "\n\t-pdfDocDir=<dir> - directory where the pdf files will be stored" +
      "\n\t[-thumbDir=<dir>] - directory where the thumbnail files will be stored" +
      "\n\t[-solrColUrl=<url>] - solr collection url. For ex: http://localhost:8983/solr/myCollection"+
      "\n\t[-communities=com1,com2,..,comN] - communities used to filter documents to generate de pdf/thumbnail files" +
      "\n\t[-fromDate=YYYY-MM-DD] - initial date used to filter documents to generate de pdf/thumbnail files" +
      "\n\t[-thumbServUrl=<url>] - the thumbnail server url to be stored in metadata documents. For ex: http://localhost:9090/getDocument" +
      "\n\t[--reset] - if present will create an empty collection to store pdf/thumbnail files"
    )
    System.exit(1)
  }
  if (args.length < 1) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map, par) =>
      val split = par.split(" *= *", 2)

      split.size match {
        case 1 => map + (split(0).substring(2) -> "")
        case 2 => map + (split(0).substring(1) -> split(1))
        case _ => usage();map
      }
  }

  val fiadminApi = "http://fi-admin.bvsalud.org/api"
  val pdfDocDir = parameters("pdfDocDir").trim
  val thumbServUrl = parameters.get("thumbServUrl").map { turl => // http:/localhost:9090/getDocument
     val url = turl.trim
     if (url.endsWith("/")) url else s"$url/"
  }
  val solrColUrl = parameters.get("solrColUrl").map(_.trim)
  val thumbDir = parameters.get("thumbDir").map(_.trim)
  val communities = parameters.get("communities").map(_.trim.split(" *\\, *"))
  val fromDate = parameters.get("fromDate").map(_.trim)
  val reset = parameters.contains("reset")
  val date = "(19[789]|20[01])[0-9]\\-(0[1-9]|1[012])\\-[01][0-9]"

  fromDate.foreach(fdate => if (!fdate.matches(date)) usage())

  update(pdfDocDir, solrColUrl, thumbDir, communities, fromDate, reset)

  def update(pdfDocDir: String,
             solrColUrl: Option[String],
             thumbDir: Option[String],
             communities: Option[Array[String]],
             fromDate: Option[String],
             reset: Boolean): Unit = {
    if (reset) Tools.deleteDirectory(new File(pdfDocDir))

    val lpds: LocalPdfDocServer = new LocalPdfDocServer(new FSDocServer(new File(pdfDocDir)))
    val lpss: Option[LocalPdfSrcServer] = solrColUrl.map {
      solr =>
        val sds = new SolrDocServer(solr)
        if (reset) {
          sds.deleteCollection()
          sds.createCollection()
        }
        new LocalPdfSrcServer(sds, Right(lpds))
    }
    val lts: Option[LocalThumbnailServer] = thumbDir.map {
      thumb =>
        if (reset) Tools.deleteDirectory(new File(thumb))
        new LocalThumbnailServer(new FSDocServer(new File(thumb)), Right(lpds))
    }

    getMetadata(communities, fromDate) foreach {
      meta =>
        val id: Option[Seq[String]] = meta.get("id")
        val url: Option[Seq[String]] = meta.get("ur")

        if (id.isEmpty || url.isEmpty) {
          if (id.isDefined) println(s"---- Empty url. id=${id.get.head}")
          else if (url.isDefined) println(s"---- Empty id. url=${url.get.head}")
          else println(s"---- Empty url and url")
        }
        else {
          val idStr: String = id.get.head
          val urlStr: String = url.get.head

          lpss.foreach { pss =>
            println(s"+++ loading and indexing pdf document id=$idStr url=$urlStr")
            if (pss.createDocument(idStr, urlStr, Some(meta)) == 201) {
              lts.foreach { ts =>
                //println(s"+++ creating thumbnail document id=$idStr url=$urlStr")
                if (ts.createDocument(idStr, urlStr, None) != 201)
                  println(s"---- LocalThumbnailServer document creation error. id=$idStr url=$urlStr")
              }
            } else
              println(s"---- LocalPdfSrcServer document creation error. id=$idStr url=$urlStr")
          }
          if (lpss.isEmpty && lts.isEmpty && (lpds.createDocument(idStr, urlStr, None) != 201)) {
            println(s"---- LocalPdfDocServer document creation error. id=$idStr url=$urlStr")
          }
        }
    }
  }

  private def getMetadata(communities: Option[Array[String]],
                          fromDate: Option[String]): Seq[Map[String,Seq[String]]] = {
    val commIds: Set[String] = communities match {
      case Some(cIds: Array[String]) => Set[String]() ++ cIds
      case None => getCommunityIds.getOrElse(Set[String]())
    }
    val fromDateStr: String = fromDate match {
      case Some(fromD) => s"&updated_time__gte=$fromD"
      case None => ""
    }

    commIds.foldLeft(Seq[Map[String,Seq[String]]]()) {
      case (_, cid) => getCollectionIds(cid) match {
        case Some(ids) =>
          ids.foldLeft(Seq[Map[String, Seq[String]]]()) {
            case (seq2, id) =>
              //println(s"+++ parsing metadata community:$cid collection:$id")
              seq2 ++ getMetadata(cid, id, fromDateStr)
          }
        case None => Seq[Map[String, Seq[String]]]()
      }
    }
  }

  private def getMetadata(comId: String,
                          colId: String,
                          fromDate: String): Seq[Map[String,Seq[String]]] = {
    Try {
      val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&status=1&format=json$fromDate", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json) => json match {
        case Right(js: Json) => getMetadata(comId, colId,
          js.hcursor.downField("objects").downArray.first, Seq[Map[String,Seq[String]]]())
        case Left(_) => Seq[Map[String,Seq[String]]]()
      }
      case Failure(_) => Seq[Map[String,Seq[String]]]()
    }
  }

  private def getMetadata(comId: String,
                         colId: String,
                         elem: ACursor,
                         seq: Seq[Map[String,Seq[String]]]): Seq[Map[String,Seq[String]]] = {
    if (elem.succeeded) {
      val aux = seq :+ getMetadata(comId, colId, elem)
      getMetadata(comId, colId, elem.right, aux)
    } else seq
  }

  private def getMetadata(comId: String,
                          colId: String,
                          elem: ACursor): Map[String,Seq[String]] = {
    val docId = parseId(elem)
    val url = parseDocUrl(elem)

    println(s"+++ parsing metadata - community:$comId collection:$colId document:${docId.head}")

    Map(
      "id" -> docId,
      "ti" -> parseTitle(elem),
      "type" -> parseDocType(elem),
      "au" -> parseAuthor(elem),
      "da" -> parseYear(elem),
      "kw" -> parseKeyword(elem),
      "fo" -> parseSource(elem),
      "ab" -> parseAbstr(elem),
      "ur" -> url,
      "la" -> parseLanguage(elem),
      "mh" -> parseDescriptor(elem),
      "com" -> Seq(comId), //parseCommunity(elem),
      "col" -> Seq(colId), //parseCollection(elem),
      "ud" -> parseUpdDate(elem),
      "tu" -> parseThumbUrl(docId.head, url.head)
    ).filterNot(kv => kv._2.isEmpty)
  }

  private def getCommunityIds: Option[Set[String]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/community?format=json", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json: Either[ParsingFailure, Json]) =>
        json match {
          case Right(js) => Some(getCommunityIds(js.hcursor.downField("objects").downArray.first, Set[String]()))
          case Left(_) => None
        }
      case Failure(_) => None
    }
  }

  private def getCommunityIds(elem: ACursor,
                              set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val aux: Set[String] = elem.downField("id").as[Int] match {
        case Right(id) =>
          set + id.toString
        case _ => set
      }
      getCommunityIds(elem.right, aux)
    } else set
  }

  private def getCollectionIds(communityId: String): Option[Set[String]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/collection/?community=$communityId&format=json", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json: Either[ParsingFailure, Json]) =>
        json match {
          case Right(js) => Some(getCollectionIds(js.hcursor.downField("objects").downArray.first, Set[String]()))
          case Left(_) => None
        }
      case Failure(_) => None
    }
  }

  private def getCollectionIds(elem: ACursor,
                               set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val aux: Set[String] = elem.downField("id").as[Int] match {
        case Right(id) => set + id.toString
        case _ => set
      }
      getCollectionIds(elem.right, aux)
    } else set
  }

  private def parseId(elem: ACursor): Seq[String] = {
    elem.downField("id").as[Int] match {
      case Right(id: Int) => Seq[String](id.toString)
      case Left(_) => Seq[String]()
    }
  }

  private def parseTitle(elem: ACursor): Seq[String] = {
    parseTitle(elem.downField("title_monographic").downArray.first, Seq[String]())
  }

  private def parseTitle(elem: ACursor,
                         seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseTitle(elem.right, seq)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseTitle(elem.right, seq :+ langTxt)
      }
    } else seq
  }

  private def parseDocType(elem: ACursor): Seq[String] = Seq(elem.downField("literature_type").as[String].getOrElse(""))

  /*
    individual_author_monographic
    corporate_author_monographic
    individual_author_collection
    corporate_author_collection
    individual_author
    corporate_author
   */
  private def parseAuthor(elem: ACursor): Seq[String] = {
    val e = elem.downField("individual_author_monographic").downArray.first
    if (e.succeeded) parseAuthor(e, Seq[String]())
    else {
      val e = elem.downField("corporate_author_monographic").downArray.first
      if (e.succeeded) parseAuthor(e, Seq[String]())
      else {
        val e = elem.downField("individual_author_collection").downArray.first
        if (e.succeeded) parseAuthor(e, Seq[String]())
        else {
          val e = elem.downField("corporate_author_collection").downArray.first
          if (e.succeeded) parseAuthor(e, Seq[String]())
          else {
            val e = elem.downField("individual_author").downArray.first
            if (e.succeeded) parseAuthor(e, Seq[String]())
            else {
              val e = elem.downField("corporate_author").downArray.first
              if (e.succeeded) parseAuthor(e, Seq[String]())
              else Seq[String]()
            }
          }
        }
      }
    }
  }

  private def parseAuthor(elem: ACursor,
                          seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseAuthor(elem.right, seq)
      else parseAuthor(elem.right, seq :+ text)
    } else seq
  }

  private def parseYear(elem: ACursor): Seq[String] = {
    val date = elem.downField("publication_date_normalized").as[String].getOrElse("")
    val size = date.length

    Seq(if (size == 0) ""
        else if (size < 4) date
        else date.substring(0,4))
  }

  private def parseKeyword(elem: ACursor): Seq[String] = {
    parseKeyword(elem.downField("author_keyword").downArray.first, Seq[String]())
  }

  private def parseKeyword(elem: ACursor,
                         seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseKeyword(elem.right, seq)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseKeyword(elem.right, seq :+ langTxt)
      }
    } else seq
  }

  private def parseSource(elem: ACursor): Seq[String] = Seq(elem.downField("source").as[String].getOrElse(""))

  private def parseAbstr(elem: ACursor): Seq[String] = {
    parseAbstr(elem.downField("abstract").downArray.first, Seq[String]())
  }

  private def parseAbstr(elem: ACursor,
                         seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseAbstr(elem.right, seq)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseAbstr(elem.right, seq :+ langTxt)
      }
    } else seq
  }

  private def parseDocUrl(elem: ACursor): Seq[String] = {
    elem.downField("electronic_address").downArray.first.downField("_u").as[String] match {
      case Right(url) => Seq[String](url)
      case _ => Seq[String]()
    }
  }

  private def parseLanguage(elem: ACursor): Seq[String] = {
    parseLanguage(elem.downField("text_language").downArray.first, Seq[String]())
  }

  private def parseLanguage(elem: ACursor,
                            seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseLanguage(elem.right, seq)
      else parseLanguage(elem.right, seq :+ text)
    } else seq
  }

  private def parseDescriptor(elem: ACursor): Seq[String] = {
    val primary = parseDescriptor(elem.downField("descriptors_primary").downArray.first, Seq[String]())
    val secondary = parseDescriptor(elem.downField("descriptors_secondary").downArray.first, Seq[String]())

    primary ++ secondary
  }

  private def parseDescriptor(elem: ACursor,
                              seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseDescriptor(elem.right, seq)
      else parseDescriptor(elem.right, seq :+ text)
    } else seq
  }

  //private def parseCommunity(elem: ACursor): Seq[String] = Seq[String]()

  //private def parseCollection(elem: ACursor): Seq[String] = Seq[String]()

  private def parseUpdDate(elem: ACursor): Seq[String] = Seq(elem.downField("updated_time").as[String].getOrElse(""))

  private def parseThumbUrl(id: String,
                            url: String): Seq[String] = thumbServUrl match {
    case Some(tsu) => Seq(s"$tsu?id=$id&url=$url")
    case None      => Seq("")
  }
}
