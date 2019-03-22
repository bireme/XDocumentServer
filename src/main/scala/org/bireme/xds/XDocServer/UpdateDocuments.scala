/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, File, IOException}

import io.circe._
import io.circe.parser._

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

//https://github.com/bireme/fi-admin/wiki/API

class UpdateDocuments(pdfDocDir: String,
                      solrColUrl: String,
                      thumbDir: String,
                      thumbServUrl: Option[String]) {
  val fiadminApi = "https://fi-admin.bvsalud.org/api"

  val pdfDocServer = new FSDocServer(new File(pdfDocDir))
  //val pdfDocServer = new SwayDBServer(new File(pdfDocDir))
  val lpds: LocalPdfDocServer = new LocalPdfDocServer(pdfDocServer)
  val lpss: LocalPdfSrcServer = new LocalPdfSrcServer(new SolrDocServer(solrColUrl), lpds)
  val thumbDocServer = new FSDocServer(new File(thumbDir))
  //val thumbDocServer = new SwayDBServer(new File(thumbDir))
  val lts: LocalThumbnailServer = new LocalThumbnailServer(thumbDocServer, Right(lpds))

  /**
    * Update the contents of only one document (metadata + thumbnail)
    * @param docId the document id
    * @return true if the update was a success or false otherwise
    */
  def updateOne(docId: String): Boolean = {
    assert(docId != null)

    getMetadata(docId) exists {
      meta: Map[String, Seq[String]] =>
        val id: Option[Seq[String]] = meta.get("id")
        val url: Option[Seq[String]] = meta.get("ur")

        if (id.isEmpty || url.isEmpty) false
        else {
          val idStr: String = id.get.head
          val urlStr: String = url.get.head

          Tools.url2ByteArray(urlStr).exists {
            arr =>
              val bais = new ByteArrayInputStream(arr)
              bais.mark(Integer.MAX_VALUE)
              Try {
                val b1: Boolean = lpss.replaceDocument(idStr, bais, Some(meta)) != 500 // LocalPdfSrcServer
                bais.reset()
                b1 && lts.replaceDocument(idStr, bais, None) != 500 // LocalThumbnailServer
              }.isSuccess
          }
        }
    }
  }

  /**
    * Store only the pdfs and thumbnails that are not already stored
    */
  def addMissing(): Unit = {
    val (_, lpssIds, ltsIds) = getStoredIds(lpss, lts)

    val metad = getMetadata(Set[String]())
     metad foreach {
      meta =>
        val id: Option[Seq[String]] = meta.get("id")
        val url: Option[Seq[String]] = meta.get("ur")

        if (id.isEmpty && url.isEmpty) println("id and url are empty")
        else if (id.isEmpty) println(s"\n--- Empty id. url=${url.get.head}")
        else if (url.isEmpty) println(s"\n--- Empty url. id=${id.get.head}")
        else {
          val idStr: String = id.get.head
          val urlStr: String = url.get.head
          val lpssContains = lpssIds.contains(idStr)
          val lstContains = ltsIds.contains(idStr)

          if (!lpssContains || !lstContains) {
            println(s"\nProcessing document:$idStr")
            if (!lpssContains) {
              val code = lpss.createDocument(idStr, urlStr, Some(meta))
              val prefix = if (code == 201) "+++" else "---"
              val status = if (code == 201) "OK" else "ERROR"
              println(s"$prefix LocalPdfSrcServer document creation $status. id=$idStr url=$urlStr code=$code")
            }
            if (!lstContains) {
              val code = lts.createDocument(idStr, urlStr, None)
              val prefix = if (code == 201) "+++" else "---"
              val status = if (code == 201) "OK" else "ERROR"
              println(s"$prefix LocalThumbnailServer document creation $status. id=$idStr url=$urlStr code=$code")
            }
          }
        }
    }
  }

  /**
    * Update document whose updated_time field changed compared to FI-Admin field.
    */
  def updateChanged(): Unit = {
    val (_, lpssIds, ltsIds) = getStoredIds(lpss, lts)
    val storedIds: Set[String] = lpssIds ++ lpssIds

    storedIds.foreach {
      docId =>
        println(s"Checking document:$docId [if changed]")
        getMetadata(docId) match {
          case Right(meta) =>
            if (meta.isEmpty) {
              if (lpssIds.contains(docId)) {
                val code1 = lpss.deleteDocument(docId)
                val prefix1 = if (code1 == 200) "+++" else "---"
                val status1 = if (code1 == 200) "OK" else "ERROR"
                println(s"$prefix1 LocalPdfSrcServer document deletion $status1. id=$docId code=$code1")
              }
              if (ltsIds.contains(docId)) {
                val code2 = lts.deleteDocument(docId)
                val prefix2 = if (code2 == 200) "+++" else "---"
                val status2 = if (code2 == 200) "OK" else "ERROR"
                println(s"$prefix2 LocalThumbnailServer document deletion $status2. id=$docId code=$code2")
              }
            } else {
              meta.get("ud") match {
                case Some(utime) =>
                  lpss.getDocumentInfo(docId) match {
                    case Right(meta2) =>
                      meta2.get("ud") match {
                        case Some(updTime) =>
                          if (updTime.isEmpty || !utime.head.equals(updTime.head)) {
                            if (updateOne(docId)) println(s"+++ document updated OK. id=$docId")
                            else println(s"--- document updated ERROR. id=$docId")
                          }
                        case None => println(s"--- document 'ud' metadata missing ERROR. id=$docId code=404")
                      }
                    case Left(errCode) => println(s"--- document metadata ERROR. id=$docId code=$errCode")
                  }
                case None => println(s"--- FI-Admin document 'updated_time' metadata missing ERROR. id=$docId code=404")
              }
            }
          case Left(msg) => println(s"--- FI-Admin document metadata retrieval ERROR. id=$docId code=500 msg=$msg")
        }
    }
  }

  /**
    * Store and index all pdfs and thumbnails
    */
  def addAll(): Unit = {
    lpss.deleteDocuments()
    lts.deleteDocuments()

    getMetadata(Set[String]()) foreach {
      meta =>
        val id: Option[Seq[String]] = meta.get("id")
        val url: Option[Seq[String]] = meta.get("ur")

        if (id.isEmpty && url.isEmpty) println("id and url are empty")
        else if (id.isEmpty) println(s"---- Empty id. url=${url.get.head}")
        else if (url.isEmpty) println(s"---- Empty url. id=${id.get.head}")
        else {
          val idStr: String = id.get.head
          val urlStr: String = url.get.head

          println(s"\nProcessing document:$idStr")
          val code1 = lpss.createDocument(idStr, urlStr, Some(meta))
          val prefix1 = if (code1 == 201) "+++" else "---"
          val status1 = if (code1 == 201) "OK" else "ERROR"
          println(s"$prefix1 LocalPdfSrcServer document creation $status1. id=$idStr url=$urlStr code=$code1")

          val code2 = lts.createDocument(idStr, urlStr, None)
          val prefix2 = if (code2 == 201) "+++" else "---"
          val status2 = if (code2 == 201) "OK" else "ERROR"
          println(s"$prefix2 LocalThumbnailServer document creation $status2. id=$idStr url=$urlStr code=$code2")
        }
    }
  }

  /**
    * Get the ids that are stored at LocalPdfSrcServer and at LocalThumbnailServer
    * @param lpss the LocalPdfSrcServer object
    * @param lts the LocalThumbnailServer object
    * @return a triple of (ids stored at LocalPdfSrcServer and LocalThumbnailServer,
    *                      ids stored at LocalPdfSrcServer,
    *                      ids stored at LocalThumbnailServer)
    */
  private def getStoredIds(lpss: LocalPdfSrcServer,
                           lts: LocalThumbnailServer): (Set[String], Set[String], Set[String]) = {
    val docs1 = lpss.getDocuments
    val docs2 = lts.getDocuments

    (docs1.intersect(docs2), docs1, docs2)
  }

  private def getMetadata(storedIds: Set[String]): Seq[Map[String,Seq[String]]] = {
    val commIds: Set[String] = getCommunityIds.getOrElse(Set[String]())

    commIds.foldLeft(Seq[Map[String,Seq[String]]]()) {
      case (seq1, cid) => getCollectionIds(cid) match {
        case Some(ids) =>
          ids.foldLeft(seq1) {
            case (seq2, id) =>
              //println(s"***+++ parsing metadata community:$cid collection:$id")
              seq2 ++ getMetadata(cid, id, storedIds)
          }
        case None => Seq[Map[String, Seq[String]]]()
      }
    }
  }

  private def getMetadata(comId: String,
                          colId: String,
                          storedIds: Set[String]): Seq[Map[String,Seq[String]]] = {
    Try {
      val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&status=1&limit=0&format=json", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json) => json match {
        case Right(js: Json) => getMetadata(comId, colId,
          js.hcursor.downField("objects").downArray.first, storedIds, Seq[Map[String,Seq[String]]]())
        case Left(_) => Seq[Map[String,Seq[String]]]()
      }
      case Failure(_) => Seq[Map[String,Seq[String]]]()
    }
  }

  private def getMetadata(docId: String): Either[String, Map[String,Seq[String]]] = {
    Try {
      val src = Source.fromURL(s"$fiadminApi/bibliographic/?id=$docId&status=1&limit=1&format=json", "utf-8")
      val content: String = src.getLines().mkString("\n").trim
      src.close()

      if (content.startsWith("{\"error\"")) throw new IOException(content)
      parse(content)
    } match {
      case Success(json) => json match {
        case Right(js: Json) => Right(getMetadata(js.hcursor.downField("objects").downArray.first)
                                      .getOrElse(Map[String,Seq[String]]()))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) => Left(ex.toString)
    }
  }

  private def getMetadata(elem: ACursor): Option[Map[String,Seq[String]]] = {
    if (elem.succeeded) {
      val docId: Seq[String] = parseId(elem)
      val url: Seq[String] = parseDocUrl(elem)
      val comId2: Seq[String] = parseCommunity(elem)
      val colId2: Seq[String] = parseCollection(elem)

      val map: Map[String, Seq[String]] = Map(
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
        "com" -> comId2,
        "col" -> colId2,
        "ud" -> parseUpdDate(elem),
        "tu" -> parseThumbUrl(docId.head, if (url.isEmpty) "" else url.head)
      )
      Some(map.filterNot(kv => kv._2.isEmpty))
    } else None
  }

  private def getMetadata(comId: String,
                          colId: String,
                          elem: ACursor,
                          storedIds: Set[String],
                          seq: Seq[Map[String,Seq[String]]]): Seq[Map[String,Seq[String]]] = {
    if (elem.succeeded) {
      val map = getMetadata(comId, colId, elem, storedIds)
      val aux = if (map.isEmpty) seq
                else seq :+ map
      getMetadata(comId, colId, elem.right, storedIds, aux)
    } else seq
  }

  private def getMetadata(comId: String,
                          colId: String,
                          elem: ACursor,
                          storedIds: Set[String]): Map[String,Seq[String]] = {
    val docId: Seq[String] = parseId(elem)

    if (docId.isEmpty || storedIds.contains(docId.head)) Map[String,Seq[String]]()
    else {
      val url: Seq[String] = parseDocUrl(elem)
      val comId2: Seq[String] = parseCommunity(elem)
      val colId2: Seq[String] = parseCollection(elem)

      println(s"+++ parsing metadata - community:$comId collection:$colId document:${docId.head}")

      val map: Map[String, Seq[String]] = Map(
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
        "com" -> comId2,
        "col" -> colId2,
        "ud" -> parseUpdDate(elem),
        "tu" -> parseThumbUrl(docId.head, if (url.isEmpty) "" else url.head)
      )
      map.filterNot(kv => kv._2.isEmpty)
    }
  }

  private def getCommunityIds: Option[Set[String]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/community?limit=0&format=json", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json: Either[ParsingFailure, Json]) =>
        json match {
          case Right(js) =>
            Some(getCommunityIds(js.hcursor.downField("objects").downArray.first, Set[String]()))
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
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/collection/?community=$communityId&limit=0&format=json", "utf-8")
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

  private def parseCommunity(elem: ACursor): Seq[String] = {
    parseCommunity(elem.downField("community").downArray.first, Seq[String]())
  }

  private def parseCommunity(elem: ACursor,
                             seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseCommunity(elem.right, seq)
      else {
        val text2 = text.replaceAll("\\|([^\\^]+)\\^", "\\|\\($1\\) ")
        parseCommunity(elem.right, seq :+ text2)
      }
    } else seq
  }

  private def parseCollection(elem: ACursor): Seq[String] = {
    parseCollection(elem.downField("collection").downArray.first, Seq[String]())
  }

  private def parseCollection(elem: ACursor,
                              seq: Seq[String]): Seq[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseCollection(elem.right, seq)
      else {
        val text2 = text.replaceAll("\\|([^\\^]+)\\^", "\\|\\($1\\) ")
        parseCollection(elem.right, seq :+ text2)
      }
    } else seq
  }

  private def parseUpdDate(elem: ACursor): Seq[String] = Seq(elem.downField("updated_time").as[String].getOrElse(""))

  private def parseThumbUrl(id: String,
                            url: String): Seq[String] = thumbServUrl match {
    case Some(tsu) => Seq(s"$tsu?id=$id&url=$url")
    case None      => Seq("")
  }
}

object UpdateDocuments extends App {

  private def usage(): Unit = {
    System.err.println(
      "\nusage: UpdateDocuments [OPTIONS]" +
        "\n\nOPTIONS" +
        "\n\t-pdfDocDir=<dir> - directory where the pdf files will be stored" +
        "\n\t-thumbDir=<dir> - directory where the thumbnail files will be stored" +
        "\n\t-solrColUrl=<url> - solr collection url. For ex: http://localhost:8983/solr/pdfs" +
        "\n\t[-thumbServUrl=<url>] - the thumbnail server url to be stored into metadata documents. For ex: http://localhost:9090/thumbnailServer/getDocument" +
        "\n\t[-docId=<id>] - update only one document whose id is <id>. if used, --onlyMissing parameter will br ignored" +
        "\n\t[--addMissing] - if present, will create pdf/thumbnail files only if they were not already created" +
        "\n\t[--updateChanged] - if present, will update pdf/thumbnail files whose metadata changed in the FI-Admin"
    )
    System.exit(1)
  }
  if (args.length < 3) usage()

  val parameters = args.foldLeft[Map[String, String]](Map()) {
    case (map, par) =>
      val split = par.split(" *= *", 2)

      split.size match {
        case 1 => map + (split(0).substring(2) -> "")
        case 2 => map + (split(0).substring(1) -> split(1))
        case _ => usage(); map
      }
  }
  if (!parameters.contains("pdfDocDir")) usage()
  if (!parameters.contains("thumbDir")) usage()
  if (!parameters.contains("solrColUrl")) usage()

  val pdfDocDir = parameters("pdfDocDir").trim
  val thumbDir = parameters("thumbDir").trim
  val solrColUrl = parameters("solrColUrl").trim
  val thumbServUrl: String = parameters.get("thumbServUrl") match {
    case Some(turl) => // http:/localhost:9090/thumbnailServer/getDocument
      val url = turl.trim
      if (url.endsWith("/")) url else s"$url/"
    case None =>  "http://thumbnailserver.bvsalud.org/getDocument/"
  }
  val docId = parameters.get("docId").map(_.trim)
  val addMissing = parameters.contains("addMissing")
  val updChanged = parameters.contains("updateChanged")
  val updDocuments = new UpdateDocuments(pdfDocDir, solrColUrl, thumbDir, Some(thumbServUrl))

  docId match {
    case Some(did) =>
      updDocuments.updateOne(did)
    case None =>
      val addAll: Boolean = !(addMissing || updChanged)

      if (addAll) updDocuments.addAll()
      else {
        if (addMissing) updDocuments.addMissing()
        if (updChanged) updDocuments.updateChanged()
      }
  }
}
