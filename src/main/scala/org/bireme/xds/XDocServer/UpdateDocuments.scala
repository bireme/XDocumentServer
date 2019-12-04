 /*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, File, IOException}

import bruma.master._
import io.circe._
import io.circe.parser._

import scala.collection.JavaConverters._
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

//https://github.com/bireme/fi-admin/wiki/API
//http://basalto01.bireme.br:9292/solr/#/pdfs/query

class UpdateDocuments(pdfDocDir: String,
                      solrColUrl: String,
                      thumbDir: String,
                      decsPath: String,
                      thumbServUrl: Option[String]) {
  val fiadminApi: String = "https://fi-admin.bvsalud.org/api"

  val pdfDocServer: FSDocServer = new FSDocServer(new File(pdfDocDir), Some("pdf"))
  //val pdfDocServer = new SwayDBServer(new File(pdfDocDir))
  val lpds: LocalPdfDocServer = new LocalPdfDocServer(pdfDocServer)
  val lpss: LocalPdfSrcServer = new LocalPdfSrcServer(new SolrDocServer(solrColUrl), lpds)
  val thumbDocServer = new FSDocServer(new File(thumbDir), Some("jpg"))
  //val thumbDocServer = new SwayDBServer(new File(thumbDir))
  val lts: LocalThumbnailServer = new LocalThumbnailServer(thumbDocServer, Right(lpds))
  val mst: Master = MasterFactory.getInstance(decsPath).setEncoding("ISO8859-1").open()

  /**
    * Update the contents of only one document (metadata + pdf + thumbnail)
    * @param docId the document id
    * @return true if the update was a success or false otherwise
    */
  def updateOne(docId: String): Boolean = {
    assert(docId != null)

    getMetadata(docId) exists {
      meta: Map[String, Set[String]] =>
        val id: Option[Set[String]] = meta.get("id")
        val url: Option[Set[String]] = meta.get("ur")

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

    val docIds: Seq[String] = getDocumentIds.toSeq.sorted
    val total: Int = docIds.size

    docIds.zipWithIndex.foreach {
      case (docId, index) =>
        val lpssContains = lpssIds.contains(docId)
        val lstContains = ltsIds.contains(docId)

        if (!lpssContains || !lstContains) {
          println(s"\n>>> (${index + 1}/$total) Processing document id=$docId")
          getMetadata(docId) match {
            case Left(err) => println(s"--- Getting document metadata ERROR. id=$docId msg=$err")
            case Right(meta) =>
              meta.get("ur") match {
                case None => println(s"--- Getting url from metadata ERROR. id=$docId")
                case Some(url: Set[String]) =>
                  if (lpssContains) println(s"+++ LocalPdfSrcServer document was OK.")
                  else {
                    val code = lpss.createDocument(docId, url.head, Some(meta))
                    val prefix = if (code == 201) "+++" else "---"
                    val status = if (code == 201) "OK" else "ERROR"
                    println(s"$prefix LocalPdfSrcServer document creation $status. id=$docId url=${url.head} code=$code")
                  }
                  if (lstContains) println(s"+++ LocalThumbnailServer document was OK.")
                  else {
                    val code = lts.createDocument(docId, url.head, None)
                    val prefix = if (code == 201) "+++" else "---"
                    val status = if (code == 201) "OK" else "ERROR"
                    println(s"$prefix LocalThumbnailServer document creation $status. id=$docId url=${url.head} code=$code")
                  }
              }
          }
        }
    }
  }

  /**
    * Update document whose field 'ud' changed compared to FI-Admin field 'updated_time'.
    */
  def updateChanged(): Unit = {
    val (_, lpssIds, ltsIds) = getStoredIds(lpss, lts)
    val storedIds: Set[String] = lpssIds ++ ltsIds
    val totalIds: Int = storedIds.size

    storedIds.zipWithIndex.foreach {
      case (docId, index) =>
        println(s"\n>>> (${index + 1}/$totalIds) Checking document id=$docId [if changed]")
        getMetadata(docId) match {
          case Right(meta) =>
            if (meta.isEmpty) {    // Document was deleted in FI_Admin
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
            } else {              // Document is present in FI_Admin
              meta.get("ud") match { // update date
                case Some(utime) =>
                  lpss.getDocumentInfo(docId) match {
                    case Right(meta2) =>
                      meta2.get("ud") match {
                        case Some(updTime) =>
                          if (updTime.isEmpty || !utime.head.equals(updTime.head)) {  // Document has changed
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

    val docIds: Seq[String] = getDocumentIds.toSeq.sorted
    val total: Int = docIds.size

    docIds.zipWithIndex.foreach {
      case (id, index) =>
        print(s"\n>>> (${index+1}/$total) Loading id=$id ... ")

        getMetadata(id) match {
          case Right(meta) =>
            val id: Option[Set[String]] = meta.get("id")
            val url: Option[Set[String]] = meta.get("ur")

            if (id.isEmpty && url.isEmpty) println("ERROR\n---- Id and url are empty")
            else if (id.isEmpty) println(s"ERROR\n---- Empty id. url=${url.get.head}")
            else if (url.isEmpty) println(s"ERROR\n---- Empty url. id=${id.get.head}")
            else {
              val idStr: String = id.get.head
              val urlStr: String = url.get.head

              Try(Tools.url2ByteArray(urlStr)) match {
                case Success(opt) =>
                  opt match {
                    case Some(arr) =>
                      //println(s"OK\n>>> Processing document:$idStr")
                      println(s"OK")
                      val bais = new ByteArrayInputStream(arr)
                      bais.mark(Integer.MAX_VALUE)
                      val code1 = lpss.createDocument(idStr, bais, Some(meta))
                      val prefix1 = if (code1 == 201) "+++" else "---"
                      val status1 = if (code1 == 201) "OK" else "ERROR"
                      println(s"$prefix1 LocalPdfSrcServer document creation $status1. code=$code1 url=$urlStr")

                      bais.reset()
                      val code2 = lts.createDocument(idStr, bais, None)
                      val prefix2 = if (code2 == 201) "+++" else "---"
                      val status2 = if (code2 == 201) "OK" else "ERROR"
                      println(s"$prefix2 LocalThumbnailServer document creation $status2. code=$code2 url=$urlStr")
                    case None => println(s"ERROR\n---- Loading url. id=$idStr url=$urlStr")
                  }
                case Failure(msg) => println(s"ERROR\n---- Loading url. id=$idStr url=$urlStr msg=$msg")
              }
            }
          case Left(msg) => println(s"ERROR\n---- Skipping document. id=$id msg=$msg")
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

  /**
    * Get the ids that are stored at FI-admin
    * @return a set of ids that are stored at FI-admin
    */
  private def getDocumentIds: Set[String] = {
    val ids: Set[String] = getCollectionIds.getOrElse(Set[String]()).foldLeft(Set[String]()) {
      case (set, colId) =>
        Try {
          print(s"Loading ids from colId: $colId.")
          //val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&status=1&limit=0&format=json", "utf-8")
          val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&limit=0&format=json", "utf-8")
          val content = src.getLines().mkString("\n")
          src.close()
          content
        } match {
          case Success(content) =>
            val rex = "\"id\": (\\d+)".r
            val ids1 = rex.findAllMatchIn(content).foldLeft(Set[String]()) {
              case (set1, mat) => set1 + mat.group(1)
            }
            println(s"\tTotal collection ids: ${ids1.size}")
            set ++ ids1
          case Failure(_) => set
        }
    }
    println(s"Total ids: ${ids.size}")
    ids
  }

  /**
    * @param docId document identifier
    * @return a map of FI-admin document metadata (meta -> set(values)
    */
  private def getMetadata(docId: String): Either[String, Map[String,Set[String]]] = {
    val url: String = s"$fiadminApi/bibliographic/?id=$docId&limit=1&format=json"

    Try {
      //val src = Source.fromURL(s"$fiadminApi/bibliographic/?id=$docId&status=1&limit=1&format=json", "utf-8")
      val src: BufferedSource = Source.fromURL(url, "utf-8")
      val content: String = src.getLines().mkString("\n").trim
      src.close()

      if (content.startsWith("{\"error\"")) throw new IOException(content)
      parse(content)
    } match {
      case Success(json) => json match {
        case Right(js: Json) => Right(getMetadata(js.hcursor.downField("objects").downArray)
          .getOrElse(Map[String,Set[String]]()))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) =>
        println(s"ERROR - getMetadata id=$docId url=$url msg=${ex.toString}")
        Left(ex.toString)
    }
  }

  private def getMetadata(elem: ACursor): Option[Map[String,Set[String]]] = {
    if (elem.succeeded) {
      val docId: Set[String] = parseId(elem)
      val url: Set[String] = parseDocUrl(elem)
      val comId: Set[String] = parseCommunity(elem)
      val colId: Set[String] = parseCollection(elem)

      val map: Map[String, Set[String]] = Map(
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
        "com" -> comId,
        "col" -> colId,
        "ud" -> parseUpdDate(elem),
        "tu" -> parseThumbUrl(docId.head, if (url.isEmpty) "" else url.head),
        "pu" -> parsePublisher(elem)
      )
      Some(map.filterNot(kv => kv._2.isEmpty))
    } else None
  }

  private def getCollectionIds: Option[Set[String]] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$fiadminApi/collection/?limit=0&format=json", "utf-8")
      val content = src.getLines().mkString("\n")
      src.close()

      content
    } match {
      case Success(content) =>
        val rex = "\"id\": (\\d+)".r
        Some(rex.findAllMatchIn(content).foldLeft(Set[String]()) {
          case (set, mat) => set + mat.group(1)
        })
      case Failure(_) => None
    }
  }

  private def parseId(elem: ACursor): Set[String] = {
    elem.downField("id").as[Int] match {
      case Right(id: Int) => Set[String](id.toString)
      case Left(_) => Set[String]()
    }
  }

  private def parseTitle(elem: ACursor): Set[String] = {
    parseTitle(elem.downField("title_monographic").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseTitle(elem: ACursor,
                         seq: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseTitle(elem.right, seq)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseTitle(elem.right, seq + langTxt)
      }
    } else seq
  }

  private def parseDocType(elem: ACursor): Set[String] = Set(elem.downField("literature_type").as[String].getOrElse(""))

  /*
    individual_author_monographic
    corporate_author_monographic
    individual_author_collection
    corporate_author_collection
    individual_author
    corporate_author
   */
  private def parseAuthor(elem: ACursor): Set[String] = {
    val e = elem.downField("individual_author_monographic").downArray
    if (e.succeeded) parseAuthor(e, Set[String]())
    else {
      val e = elem.downField("corporate_author_monographic").downArray
      if (e.succeeded) parseAuthor(e, Set[String]())
      else {
        val e = elem.downField("individual_author_collection").downArray
        if (e.succeeded) parseAuthor(e, Set[String]())
        else {
          val e = elem.downField("corporate_author_collection").downArray
          if (e.succeeded) parseAuthor(e, Set[String]())
          else {
            val e = elem.downField("individual_author").downArray
            if (e.succeeded) parseAuthor(e, Set[String]())
            else {
              val e = elem.downField("corporate_author").downArray
              if (e.succeeded) parseAuthor(e, Set[String]())
              else Set[String]()
            }
          }
        }
      }
    }
  }

  @scala.annotation.tailrec
  private def parseAuthor(elem: ACursor,
                          set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseAuthor(elem.right, set)
      else parseAuthor(elem.right, set + text)
    } else set
  }

  private def parseYear(elem: ACursor): Set[String] = {
    val date = elem.downField("publication_date_normalized").as[String].getOrElse("")
    val size = date.length

    Set(if (size == 0) ""
        else if (size < 4) date
        else date.substring(0,4))
  }

  private def parseKeyword(elem: ACursor): Set[String] = {
    parseKeyword(elem.downField("author_keyword").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseKeyword(elem: ACursor,
                           set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseKeyword(elem.right, set)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseKeyword(elem.right, set + langTxt)
      }
    } else set
  }

  private def parseSource(elem: ACursor): Set[String] = Set(elem.downField("source").as[String].getOrElse(""))

  private def parseAbstr(elem: ACursor): Set[String] = {
    parseAbstr(elem.downField("abstract").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseAbstr(elem: ACursor,
                         set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("")
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseAbstr(elem.right, set)
      else {
        val langTxt = if (lang.isEmpty) lang else s"($lang) $text"
        parseAbstr(elem.right, set + langTxt)
      }
    } else set
  }

  private def parseDocUrl(elem: ACursor): Set[String] = parseUrl(elem.downField("electronic_address").downArray)

  /**
    * @param elem an array element
    * @return the url in the array that has the type 'pdf'
    */
  @scala.annotation.tailrec
  private def parseUrl(elem: ACursor): Set[String] = {
    if (elem.succeeded) {
      elem.downField("_u").as[String] match {
        case Right(url) =>
          elem.downField("_q").as[String] match {
            case Right(docType) =>
              if (docType.trim.toLowerCase.equals("pdf")) Set(url)
              else parseUrl(elem.right)
            case _ => parseUrl(elem.right)
          }
        case _ => parseUrl(elem.right)
      }
    } else Set[String]()
  }

  private def parseLanguage(elem: ACursor): Set[String] = {
    parseLanguage(elem.downField("text_language").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseLanguage(elem: ACursor,
                            set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseLanguage(elem.right, set)
      else parseLanguage(elem.right, set + text)
    } else set
  }

  private def parseDescriptor(elem: ACursor): Set[String] = {
    val primary: Set[String] = parseDescriptor(elem.downField("descriptors_primary").downArray, Set[String]())
    //val secondary: Set[String] = parseDescriptor(elem.downField("descriptors_secondary").downArray, Set[String]())
    val secondary: Set[String] = Set[String]()  // Renato 20191121

    getDescriptorsText(primary ++ secondary)
  }

  private def getDescriptorsText(descr: Set[String]): Set[String] = {
    descr.map(_.trim).foldLeft(Set[String]()) {
      case (set, des) =>
        if (des.startsWith("^d")) {
          val content: String = des.substring(2)
          Try(Integer.parseInt(content)) match {
            case Success(mfn) => set ++ getDescriptorText(mfn)
            case Failure(_) => set + content
          }
        } else set + des
    }
  }

  private def getDescriptorText(mfn: Int): Set[String] = {
    Try {
      val record: Record = mst.getRecord(mfn)
      val set1: Set[String] = Seq(1,2,3,4).foldLeft(Set[String]()) {
        case (set, fld) =>
          Try(record.getField(fld, 1).getContent) match {
            case Success(content) =>
              Option(content) match {
                case Some(content1) => set + Tools.uniformString(content1)
                case None => set
              }
            case Failure(_) => set
          }
      }
      val set2: Set[String] = getFldSyns(record, 50)
      val set3: Set[String] = getFldSyns(record, 23)

      set1 ++ set2 ++ set3
    } match {
      case Success(value) => value
      case Failure(_) => Set[String]()
    }
  }

  private def getFldSyns(rec: Record,
                         tag: Int): Set[String] = {
    val subIds = Set('i', 'e', 'p')

    rec.getFieldList(tag).asScala.foldLeft[Set[String]](Set()) {
      case (set,fld) => fld.getSubfields.asScala.
        filter(sub => subIds.contains(sub.getId)).foldLeft[Set[String]](set) {
        case (s,sub) => s + Tools.uniformString(sub.getContent.trim())
      }
    }
  }

  @scala.annotation.tailrec
  private def parseDescriptor(elem: ACursor,
                              set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseDescriptor(elem.right, set)
      else parseDescriptor(elem.right, set + text)
    } else set
  }

  private def parseCommunity(elem: ACursor): Set[String] = {
    parseCommunity(elem.downField("community").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseCommunity(elem: ACursor,
                             set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseCommunity(elem.right, set)
      else {
        val text2 = text.replaceAll("\\|([^\\^]+)\\^", "\\|\\($1\\) ")
        parseCommunity(elem.right, set + text2)
      }
    } else set
  }

  private def parseCollection(elem: ACursor): Set[String] = {
    parseCollection(elem.downField("collection").downArray, Set[String]())
  }

  @scala.annotation.tailrec
  private def parseCollection(elem: ACursor,
                              set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseCollection(elem.right, set)
      else {
        val text2 = text.replaceAll("\\|([^\\^]+)\\^", "\\|\\($1\\) ")
        parseCollection(elem.right, set + text2)
      }
    } else set
  }

  private def parseUpdDate(elem: ACursor): Set[String] = Set(elem.downField("updated_time").as[String].getOrElse(""))

  private def parsePublisher(elem: ACursor): Set[String] = Set(elem.downField("publisher").as[String].getOrElse(""))

  private def parseThumbUrl(id: String,
                            url: String): Set[String] = thumbServUrl match {
    case Some(tsu) => Set(s"$tsu?id=$id&url=$url")
    case None      => Set("")
  }
}

object UpdateDocuments extends App {

  private def usage(): Unit = {
    System.err.println(
      "\nusage: UpdateDocuments [OPTIONS]" +
        "\n\nOPTIONS" +
        "\n\t-pdfDocDir=<dir> - directory where the pdf files will be stored" +
        "\n\t-thumbDir=<dir> - directory where the thumbnail files will be stored" +
        "\n\t-decsPath=<path> - decs master file path" +
        "\n\t-solrColUrl=<url> - solr collection url. For ex: http://localhost:8983/solr/pdfs" +
        "\n\t[-thumbServUrl=<url>] - the thumbnail server url to be stored into metadata documents. For ex: http://localhost:9090/thumbnailServer/getDocument" +
        "\n\t[-docId=<id>] - update only one document whose id is <id>. if used, --onlyMissing parameter will br ignored" +
        "\n\t[--addMissing] - if present, will create pdf/thumbnail files only if they were not already created" +
        "\n\t[--updateChanged] - if present, will update pdf/thumbnail files whose metadata changed in the FI-Admin"
    )
    System.exit(1)
  }
  if (args.length < 4) usage()

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
  val decsPath = parameters("decsPath").trim
  val solrColUrl = parameters("solrColUrl").trim
  val thumbServUrl: String = parameters.get("thumbServUrl") match {
    case Some(turl) => // http:/localhost:9090/thumbnailServer/getDocument
      val url = turl.trim
      if (url.endsWith("/")) url else s"$url/"
    case None => "http://thumbnailserver.bvsalud.org/getDocument/"
  }
  val docId = parameters.get("docId").map(_.trim)
  val addMissing = parameters.contains("addMissing")
  val updChanged = parameters.contains("updateChanged")
  val updDocuments = new UpdateDocuments(pdfDocDir, solrColUrl, thumbDir, decsPath, Some(thumbServUrl))

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
