 /*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, File, IOException}
import java.nio.file.Files
import java.util.regex.Pattern
import bruma.master._
import io.circe._
import io.circe.parser._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.util.matching.Regex
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

//https://github.com/bireme/fi-admin/wiki/API
//http://basalto01.bireme.br:9293/solr/#/pdfs/query
//curl http://localhost:9293/solr/admin/cores?action=STATUS

class UpdateDocuments(pdfDocDir: String,
                      solrColUrl: String,
                      thumbDir: String,
                      decsPath: String,
                      thumbServUrl: Option[String]) {
  val fiadminApi: String = "https://fi-admin-api.bvsalud.org/api"
  //val fiadminApi: String = "https://fi-admin.teste.bvsalud.org/api"
  val infoSources: Map[String,String] = Map("bibliographic" -> "biblio",
                                            "leisref" -> "leisref",
                                            "multimedia" -> "multimedia")

  val pdfDocServer: DocumentServer = new FSDocServer(new File(pdfDocDir), Some("pdf"))
  //val pdfDocServer = new SwayDBServer(new File(pdfDocDir))
  val lpds: LocalPdfDocServer = new LocalPdfDocServer(pdfDocServer)
  val sds: SolrDocServer = new SolrDocServer(solrColUrl)
  val lpss: LocalPdfSrcServer = new LocalPdfSrcServer(sds, lpds)
  val thumbDocServer: DocumentServer = new FSDocServer(new File(thumbDir), Some("jpg"))
  //val thumbDocServer = new SwayDBServer(new File(thumbDir))
  val lts: LocalThumbnailServer = new LocalThumbnailServer(thumbDocServer, Some(Right(lpds)))
  val mst: Master = MasterFactory.getInstance(decsPath).setEncoding("ISO8859-1").open()
  val mtRecognizer: MediaTypeRecognizer = new MediaTypeRecognizer()

  Tools.disableCertificateValidation()

  // https://stackoverflow.com/questions/16541627/javax-net-ssl-sslexception-received-fatal-alert-protocol-version
  //System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3")

  /**
    * Update the contents of only one document (metadata + pdf + thumbnail)
    * @param docId the document id (format: (<is>_<num> , for ex, leisref_55433)
    * @return true if the update was a success or false otherwise
    */
  def updateOne(docId: String): Boolean = {
    assert(docId != null)

    getMetadata(docId) match {
      case Left(err) =>
        println(s"--- Getting document metadata ERROR. id=$docId msg=$err")
        false
      case Right(meta) =>
        val id: Option[Set[String]] = meta.get("id")
        val url: Option[Set[String]] = meta.get("ur")

        if (id.isEmpty || url.isEmpty) false
        else {

          val mtype: MediaType = getMediaType(meta)
          //val status: Option[Set[String]] = meta.get("status")
          val idStr: String = id.get.head
          val urlStr: String = url.get.head

          mtype match {
            case Pdf =>
              //if (status.isDefined && status.get.head.equals("1")) {
                Tools.url2ByteArray(urlStr).exists {
                  arr =>
                    val bais = new ByteArrayInputStream(arr)
                    bais.mark(Integer.MAX_VALUE)
                    Try {
                      val b1: Boolean = lpss.replaceDocument(idStr, bais, Some(meta)) != 500 // LocalPdfSrcServer
                      bais.reset()
                      b1 && lts.replaceDocument(idStr, bais, Pdf, None) != 500 // LocalThumbnailServer
                    }.isSuccess
                }
              //} else (lpss.deleteDocument(idStr) != 500) && (lts.deleteDocument(idStr) != 500)
            case _ =>
              //if (status.isDefined && status.get.head.equals("1")) {
                (lts.replaceDocument(idStr, urlStr, Some(meta)) != 500) &&
                (sds.replaceDocument(idStr, urlStr, Some(meta)) != 500)
              //} else (lts.deleteDocument(idStr) != 500) && (sds.deleteDocument(idStr) != 500)

          }
        }
    }
  }

  /**
    * Store only the pdfs and thumbnails that are not already stored
    */
  def addMissing(): Unit = {
    val (_, lpssIds, ltsIds) = getStoredIds(lpss, lts)
    val sdsIds: Set[String] = sds.getDocuments

    getDocumentIds match {
      case Right(ids) =>
        val docIds: Seq[String] = ids.toSeq.sorted
        val total: Int = docIds.size

        docIds.zipWithIndex.foreach {
          case (docId, index) =>
            println(s"\n>> (${index + 1}/$total) Checking document id=$docId [if missing]")
            getMetadata(docId) match {
              case Left(err) => println(s"--- Getting document metadata ERROR. id=$docId msg=$err")
              case Right(meta) =>
                getMediaType(meta) match {
                  case Pdf => addMissingPdf(docId, lpssIds, ltsIds, meta)
                  case _   => addMissingOthers(docId, sdsIds, meta)
                }
            }
        }
      case Left(err) => println(s"--- Getting documents ids ERROR. msg=$err")
    }
  }

  private def addMissingPdf(docId: String,
                            lpssIds: Set[String],
                            ltsIds: Set[String],
                            meta: Map[String,Set[String]]): Unit = {
    val lpssContains: Boolean = lpssIds.contains(docId)
    val lstContains: Boolean = ltsIds.contains(docId)

    if (!lpssContains || !lstContains) {
      println(s">>> Adding missing document id=$docId")
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
    } else println(s"... document is not missing. id=$docId")
  }

  private def addMissingOthers(docId: String,
                               sdsIds: Set[String],
                               meta: Map[String,Set[String]]): Unit = {
    if (!sdsIds.contains(docId)) {
      println(s">>> Adding missing document id=$docId")
      val bais = new ByteArrayInputStream(Array.empty[Byte])

      val codeS = sds.createDocument(docId, bais, Some(meta))
      val prefixS = if (codeS == 201) "+++" else "---"
      val statusS = if (codeS == 201) "OK" else "ERROR"
      println(s"$prefixS SolrDocServer document creation $statusS. id=$docId code=$codeS")

      val codeT = lts.createDocument(docId, bais, getMediaType(meta), Some(meta))
      val prefixT = if (codeT == 201) "+++" else "---"
      val statusT = if (codeT == 201) "OK" else "ERROR"
      println(s"$prefixT LocalThumbnailServer document creation $statusT. id=$docId code=$codeT")
    } else println(s"... document is not missing. id=$docId")
  }

  /**
    * Update document whose field 'ud' changed compared to FI-Admin field 'updated_time'.
    */
  def updateChanged(): Unit = {
    val (_, lpssIds, ltsIds) = getStoredIds(lpss, lts)
    val storedIds: Seq[String] = (lpssIds ++ ltsIds).toSeq.sorted
    val totalIds: Int = storedIds.size

    storedIds.zipWithIndex.foreach {
      case (docId, index) =>
        println(s"\n>>> (${index + 1}/$totalIds) Checking document id=$docId [if changed]")
        sds.getDocumentInfo(docId) match {
          case Right(dmeta) =>
            getMetadata(docId) match {
              case Right(meta) =>
                meta.get("mt") match {
                  case Some(mType) =>
                    if (meta.isEmpty) deleteDocument(docId, mType.head, lpssIds, ltsIds) // Document was deleted in FI_Admin
                    else updateDocument(docId, dmeta, meta) // Document is present in FI_Admin
                  case None => println(s"--- Getting media type from metadata ERROR. id=$docId")
                }
              case Left(msg) => println(s"--- FI-Admin document metadata retrieval ERROR. id=$docId code=500 msg=$msg")
            }
          case Left(errCode) => println(s"--- document metadata ERROR. id=$docId code=$errCode")
        }
    }
  }

  private def deleteDocument(docId: String,
                             mType: String,
                             lpssIds: Set[String],
                             ltsIds: Set[String]): Unit = {
    mType.trim match {
      case "pdf" =>
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
      case "" => println(s"--- Empty media type ERROR. id=$docId")
      case _ =>
        val code1 = sds.deleteDocument(docId)
        val prefix1 = if (code1 == 200) "+++" else "---"
        val status1 = if (code1 == 200) "OK" else "ERROR"
        println(s"$prefix1 SolrDocServer document deletion $status1. id=$docId code=$code1")
        if (ltsIds.contains(docId)) {
          val code2 = lts.deleteDocument(docId)
          val prefix2 = if (code2 == 200) "+++" else "---"
          val status2 = if (code2 == 200) "OK" else "ERROR"
          println(s"$prefix2 LocalThumbnailServer document deletion $status2. id=$docId code=$code2")
        }
    }
  }

  private def updateDocument(docId: String,
                             dmeta: Map[String,Set[String]],
                             meta: Map[String,Set[String]]): Unit = {
    meta.get("ud") match { // update date
      case Some(utime) =>
        dmeta.get("ud") match {
          case Some(updTime) =>
            if (updTime.isEmpty || !utime.head.equals(updTime.head)) { // Document has changed
              if (updateOne(docId)) println(s"+++ document updated OK. id=$docId")
              else println(s"--- document updated ERROR. id=$docId")
            } else println(s"... document does not changed. id=$docId")
          case None => println(s"--- document update date ERROR. id=$docId")
        }
      case None => println(s"--- FI-Admin document 'updated_time' metadata missing ERROR. id=$docId code=404")
    }
  }

  /**
    * Store and index all pdfs and thumbnails
    */
  def addAll(): Unit = {
    lpss.deleteDocuments()
    lts.deleteDocuments()

    // Copying the default thumbnail into thumbnails directory
    Files.copy(new File("./nothumb.jpg").toPath, new File(s"$thumbDir/nothumb.jpg").toPath)

    getDocumentIds match {   // Either[String, Set[(String,String)]]  (infoSource,id)
      case Right(ids) =>
        val docIds: Seq[String] = ids.toSeq.sorted
        val total: Int = docIds.size

        docIds.zipWithIndex.foreach {
          case (id, index) =>
            print(s"\n>>> (${index + 1}/$total) Loading id=$id ... ")
            getMetadata(id) match {
              case Right(meta) =>
                val id: Option[Set[String]] = meta.get("id")
                val url: Option[Set[String]] = meta.get("ur")
                val mtype: MediaType = getMediaType(meta)

                if (id.isEmpty && url.isEmpty) println("ERROR\n---- Id and url are empty")
                else if (id.isEmpty) println(s"ERROR\n---- Empty id. url=${url.get.head}")
                else if (url.isEmpty) println(s"ERROR\n---- Empty url. id=${id.get.head}")
                else {
                  val idStr: String = id.get.head
                  val urlSet: Set[String] = url.get

                  if (urlSet.isEmpty) println(s"ERROR\n---- Empty URL. id=$idStr")
                  else {
                    val urlStr: String = url.get.head

                    mtype match {
                      case Pdf => createPdfDocument(idStr, urlStr, meta)
                      case _   => createOtherDocument(idStr, urlStr, meta)
                    }
                  }
                }
              case Left(msg) => println(s"ERROR\n---- Skipping document. id=$id msg=$msg")
            }
        }
      case Left(err) => println(s"--- Getting documents ids ERROR. msg=$err")
    }
  }

  private def createPdfDocument(id: String,
                                url: String,
                                meta: Map[String,Set[String]]): Unit = {
    Try(Tools.url2ByteArray(url)) match {
      case Success(opt) =>
        opt match {
          case Some(arr) =>
            println(s"OK")
            val bais = new ByteArrayInputStream(arr)
            bais.mark(Integer.MAX_VALUE)
            val code1 = lpss.createDocument(id, bais, Some(meta))
            val prefix1 = if (code1 == 201) "+++" else "---"
            val status1 = if (code1 == 201) "OK" else "ERROR"
            println(s"$prefix1 LocalPdfSrcServer document creation $status1. code=$code1 url=$url")

            bais.reset()
            val code2 = lts.createDocument(id, bais, Pdf, None)
            val prefix2 = if (code2 == 201) "+++" else "---"
            val status2 = if (code2 == 201) "OK" else "ERROR"
            println(s"$prefix2 LocalThumbnailServer document creation $status2. code=$code2 url=$url")
          case None => println(s"ERROR\n---- Loading url. id=$id url=$url")
        }
      case Failure(msg) => println(s"ERROR\n---- Loading url. id=$id url=$url msg=$msg")
    }
  }

  private def createOtherDocument(id: String,
                                  url: String,
                                  meta: Map[String,Set[String]]): Unit = {
    val codeS: Int = sds.createDocument(id, url, Some(meta))
    val prefixS = if (codeS == 201) "+++" else "---"
    val statusS = if (codeS == 201) "OK" else "ERROR"
    println(s"$prefixS SolrDocServer document creation $statusS. code=$codeS url=$url")

    val codeT: Int = lts.createDocument(id, url, Some(meta))
    val prefixT = if (codeT == 201) "+++" else "---"
    val statusT = if (codeT == 201) "OK" else "ERROR"
    println(s"$prefixT LocalThumbnailServer document creation $statusT. code=$codeT url=$url")
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
    val docs1: Set[String] = lpss.getDocuments
    val docs2: Set[String] = lts.getDocuments

    (docs1.intersect(docs2), docs1, docs2)
  }

  /**
    * Get the ids that are stored at FI-admin
    * @return a set of (infoSource_id) where id is that stored at FI-admin
    */
  private def getDocumentIds: Either[String, Set[String]] = {
    getCollectionIds.flatMap(getDocumentIds(_, infoSources, Set[String]()))
  }

  private def getDocumentIds(colIds: Set[String],
                             iSources: Map[String, String],
                             buffer: Set[String]): Either[String, Set[String]] = {
    if (colIds.isEmpty) Right(buffer)
    else {
      getDocumentIds(colIds.head, iSources).flatMap {
        docIds => getDocumentIds(colIds.tail, iSources, buffer ++ docIds)
      }
    }
  }

  private def getDocumentIds(colId: String,
                             iSources: Map[String,String]): Either[String, Set[String]] = {
    if (iSources.isEmpty) Right(Set[String]())
    else {
      val url: String = iSources.head._1 match {
        case "multimedia" => s"$fiadminApi/${iSources.head._1}/?collection=$colId&format=json"
        case other => s"$fiadminApi/$other/?collection=$colId&format=json" //s"$fiadminApi/$other/?collection=$colId&status=1&format=json"
      }
      getDocuments(url).flatMap {
        content =>
          val rex: Regex = "\"id\": (\\d+)".r
          val ids1: Set[String] = rex.findAllMatchIn(content).foldLeft(Set[String]()) {
            case (set1, mat) => set1 + s"${iSources.head._2}-${mat.group(1)}"
          }
          val idsSize: Int = ids1.size
          println(s"\tTotal collection[$colId][${iSources.head._1}] ids: $idsSize")
          getDocumentIds(colId, iSources.tail).map(ids1 ++ _)
      }
    }
  }

  private def getDocuments(url: String,
                           limit: Int = 100): Either[String,String] = {
    assert (!url.contains("&limit="))
    assert (!url.contains("&offset"))

    getTotalCount(url).flatMap(getDocuments(url, 0, _, limit))
  }

  @tailrec
  private def getTotalCount(url: String): Either[String, Int] = {
    Try {
      val src: BufferedSource = Source.fromURL(s"$url&limit=1", "utf-8")
      val content: String = src.getLines().mkString("\n")
      src.close()

      content
    } match {
      case Success(value) =>
        val regex = "\"total_count\": (\\d+)".r
        Right(regex.findFirstMatchIn(value).map(mat => mat.group(1).toInt).getOrElse(0))
      case Failure(ex) =>
        val msg: String = ex.getMessage
        if (msg.contains("502")) {
          println(s"warning: Bad Gateway error. Msg: [$msg]. Retrying url [$url]")
          getTotalCount(url)
        } else if (msg.contains("504")) {
          println(s"warning: Gateway Timeout error. Msg: [$msg]. Retrying url [$url]")
          getTotalCount(url)
        } else Left(msg)
    }
  }

  private def getDocuments(url: String,
                           offset: Int,
                           total: Int,
                           limit: Int): Either[String,String] = {
    assert (offset >= 0)
                       
    if (offset >= total) Right("")
    else {
      Try {
        val src: BufferedSource = Source.fromURL(s"$url&limit=$limit&offset=$offset", "utf-8")
        val content: String = src.getLines().mkString("\n")
        src.close()
        content
      } match {
        case Success(content) =>
          val noffset = offset + limit
          if (noffset < total) getDocuments(url, noffset, total, limit).map(content + _)
          else Right(content)
        case Failure(ex) =>
          val msg: String = ex.getMessage
          if (msg.contains("502")) {
            println(s"warning: Bad Gateway error. Msg: [$msg]. Retrying url [$url]")
            getDocuments(url, offset, total, limit)
          } else if (msg.contains("504")) {
            println(s"warning: Gateway Timeout error. Msg: [$msg]. Retrying url [$url]")
            getDocuments(url, offset, total, limit)
          } else Left(msg)
      }
    }
  }

  private def getCollectionIds: Either[String, Set[String]] = {
    getCommunitiesIds.flatMap(comIds =>
      getCollectionIds(comIds, Set[String]()))
  }

  private def getCollectionIds(communitiesIds: Set[String],
                               buffer: Set[String]): Either[String, Set[String]] = {
    if (communitiesIds.isEmpty) Right(buffer)
    else {
      getCollectionIds(communitiesIds.head).flatMap {
        colIds =>
          getCollectionIds(communitiesIds.tail, buffer ++ colIds)
      }
    }
  }

  private def getCollectionIds(communityId: String): Either[String, Set[String]] = {
    val url: String = s"$fiadminApi/collection/?community=$communityId&format=json"

    getDocuments(url).flatMap {
      content =>
        val rex: Regex = "\"id\": (\\d+)".r
        val set: Set[String] = rex.findAllMatchIn(content).foldLeft(Set[String]()) {
          case (set, mat) => set + mat.group(1)
        }
        if (set.isEmpty) Left(s"No collection id found in the $communityId!")
        else Right(set)
    }
  }

  private def getCommunitiesIds: Either[String, Set[String]] = {
    val eBlueInfoCommunity: String = "50"  // level 0 community
    val url: String = s"$fiadminApi/collection/?community=$eBlueInfoCommunity&format=json"

    getDocuments(url).flatMap {
      content =>
        val rex: Regex = "\"id\": *(\\d+),".r
        val set: Set[String] = rex.findAllMatchIn(content).foldLeft(Set[String]()) {
          case (set, mat) => set + mat.group(1)
        }
        if (set.isEmpty) Left("No community id found in the e-BlueInfo community!")
        else Right(set)
    }
  }

  /**
    * @param docId document identifier (<infoSource>-<num>)
    * @return a map of FI-admin document metadata (meta -> set(values)
    */
  private def getMetadata(docId: String): Either[String, Map[String,Set[String]]] = {
    Try {
      val index = docId.indexOf('-')
      if (index == -1) throw new IllegalArgumentException(s"docId:$docId")
      val id = docId.substring(index + 1)
      val iSrc = docId.substring(0, index)
      val source: Option[(String, String)] = infoSources.find(_._2.equals(iSrc))
      if (source.isEmpty) throw new IllegalArgumentException(s"missing information source")
      val url: String = s"$fiadminApi/${source.get._1}/$id/?format=json"
      val src: BufferedSource = Source.fromURL(url, "utf-8")
      val content: String = src.getLines().mkString("\n").trim
      src.close()

      if (content.startsWith("{\"error\"")) throw new IOException(content)
      (source.get._2, parse(content))
    } match {
      case Success((source, json)) => json match {
        case Right(js: Json) => Right(getMetadata(js.hcursor, source) //Right(getMetadata(js.hcursor.downField("objects").downArray, infoSource)
          .getOrElse(Map[String,Set[String]]()))
        case Left(ex) => Left(ex.toString)
      }
      case Failure(ex) =>
        println(s"ERROR - getMetadata id=$docId msg=${ex.toString}")
        Left(ex.toString)
    }
  }

  private def getMetadata(elem: ACursor,
                          infoSource: String): Option[Map[String,Set[String]]] = {
    if (elem.succeeded) {
      val docId: Set[String] = parseId(elem)
      val is: Set[String] = Set(infoSource)
      val isHead: String = is.headOption.getOrElse("")
      val url: Set[String] = parseDocUrl(elem)
      val comId: Set[String] = parseCommunity(elem)
      val colId: Set[String] = parseCollection(elem)
      val meta1: Map[String, Set[String]] = Map(
        "id" -> Set(s"${is.head}-${docId.head}"),
        "fi_admin_id" -> Set(docId.head),
        "alternate_ids" -> parseAlternateIds(elem),
        "status" -> parseStatus(elem),
        "is" -> is,
        "mt" -> parseMediaType(url.headOption.getOrElse("")),
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
        "tu" -> parseThumbUrl(docId.head,  if (url.isEmpty) "" else url.head),
        "pu" -> parsePublisher(elem)
      )
      val meta2: Map[String, Set[String]] =
        if (isHead.equals("leisref")) {
          Map(
            "at" -> parseActType(elem),
            "an" -> parseActNumber(elem),
            "oi" -> parseOrganIssuer(elem),
            "sn" -> parseSourceName(elem),
            "oe" -> parseOfficialEmenta(elem),
            "ue" -> parseUnofficialEmenta(elem)
          )
        } else if (isHead.equals("multimedia")) {
          Map(
            "co" -> parseContributors(elem),
            "ex"  -> parseExtension(elem),
            "de"  -> parseDescription(elem)
          )
        } else Map[String, Set[String]]()
      Some((meta1 ++ meta2).filterNot(kv => kv._2.isEmpty))
    } else None
  }

  private def parseAlternateIds(elem: ACursor): Set[String] = {
    parseAlternateIds(elem.downField("alternate_ids").downArray, Set[String]())
  }

  private def parseAlternateIds(elem: ACursor,
                                set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.as[String].getOrElse("")

      if (text.isEmpty) parseAuthor(elem.right, set)
      else parseAuthor(elem.right, set + text)
    } else set
  }

  private def parseStatus(elem: ACursor): Set[String] = {
    elem.downField("status").as[Int] match {
      case Right(id: Int) => Set[String](id.toString)
      case Left(_) => Set[String]()
    }
  }

  private def parseId(elem: ACursor): Set[String] = {
    elem.downField("id").as[Int] match {
      case Right(id: Int) => Set[String](id.toString)
      case Left(_) => Set[String]()
    }
  }

  private def parseTitle(elem: ACursor): Set[String] = {
    val tit: Set[String] = {
      val rt: ACursor = elem.downField("reference_title")
      if (rt.succeeded) parseTitle1(rt)
      else {
        val tit: ACursor = elem.downField("title")
        if (tit.succeeded) {
          tit.as[String] match {
            case Right(title: String) => Set[String](title)
            case Left(_) => Set[String]()
          }
        } else Set[String]()
      }
    }
    println("Title = \"" + tit.headOption.getOrElse("") + "\"")
    tit
  }

  private def parseMediaType(url: String): Set[String] = {
    url.trim match {
      case "" => Set[String]()
      case urlT => Set(mtRecognizer.getMediaType(urlT).getOrElse(Other).toString)
    }
  }

  private def parseTitle1(elem: ACursor): Set[String] = {
    if (elem.succeeded) {
      val arrElem = elem.downArray
      if (arrElem.succeeded) parseArrTitle(arrElem, Set[String]())
      else { // Probably is of type <x> text </x>
        val text: String = elem.as[String].getOrElse("")
        val idx = text.indexOf("|")

        Set((if (idx == -1) text else text.substring(idx + 1)).trim)
      }
    } else Set[String]()
  }

  @scala.annotation.tailrec
  private def parseArrTitle(elem: ACursor,
                            set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val lang: String = elem.downField("_i").as[String].getOrElse("").trim
      val text: String = elem.downField("text").as[String].getOrElse("")
      val idx = text.indexOf("|")
      val text1: String = (if (idx == -1) text else text.substring(idx + 1)).trim
      val langTxt = if (lang.isEmpty) text1 else s"($lang) $text1"
      val set1 = if (text1.isEmpty) set else set + langTxt

      parseArrTitle(elem.right, set1)
    } else set
  }

  private def parseDocType(elem: ACursor): Set[String] = elem.downField("literature_type").as[String]
    .getOrElse(elem.downField("act_type").as[String].getOrElse("")).trim.split("\\|").toSet

  /*
    individual_author_monographic
    corporate_author_monographic
    individual_author_collection
    corporate_author_collection
    individual_author
    corporate_author
    authors
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
              else {
                val e = elem.downField("authors").downArray
                if (e.succeeded)
                  parseAuthor(e, Set[String]())
                else Set[String]()
              }
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
      val text: String = elem.as[String].getOrElse(elem.downField("text").as[String].getOrElse(""))

      if (text.isEmpty) parseAuthor(elem.right, set)
      else parseAuthor(elem.right, set + text)
    } else set
  }

  private def parseYear(elem: ACursor): Set[String] = {
    val date = elem.downField("publication_date_normalized").as[String]
      .getOrElse(elem.downField("publication_date").as[String].getOrElse("").replace("-", ""))
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

  private def parseDocUrl(elem: ACursor): Set[String] = {
    val elem2: ACursor = elem.downField("electronic_address")
    if (elem2.succeeded) parseUrl(elem2.downArray)
    else {
      elem.downField("link").as[String] match {
        case Right(link) => Set[String](link)
        case Left(_) => Set[String]()
      }
    }
  }

  /**
    * @param elem an array element
    * @return the url in the array that has the type 'pdf'
    */
  @scala.annotation.tailrec
  private def parseUrl(elem: ACursor): Set[String] = {
    if (elem.succeeded) {
      elem.downField("_u").as[String] match {
        case Right(url) =>
          Set(url)
          /*elem.downField("_q").as[String] match {
            case Right(docType) =>
              if (docType.trim.toLowerCase.equals("pdf")) Set(url)
              else parseUrl(elem.right)
            case _ => parseUrl(elem.right)
          }*/
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
    val descrArr: ACursor = elem.downField("descriptors_primary").downArray
    val descrArr2: ACursor = if (descrArr.succeeded) descrArr
                             else elem.downField("descriptors").downArray
    val primary: Set[String] = parseDescriptor(descrArr2, Set[String]())
    val secondary: Set[String] = Set[String]()  // Renato 20191121

    getDescriptorsText(primary ++ secondary)
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

  private def getDescriptorsText(descr: Set[String]): Set[String] = {
    descr.map(_.trim).foldLeft(Set[String]()) {
      case (set, des) =>
        val matcher = Pattern.compile("\\^d([^\\^]+)").matcher(des)
        if (matcher.find) {
          val descr: String = matcher.group(1)
          Try(Integer.parseInt(descr)) match {
            case Success(mfn) => set ++ getDescriptorText(mfn)
            case Failure(_) => set + descr
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
      set1
    } match {
      case Success(value) => value
      case Failure(_) => Set[String]()
    }
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
    case None => Set()
  }

  private def parseActType(elem: ACursor): Set[String] = {
    elem.downField("act_type").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseActNumber(elem: ACursor): Set[String] = {
    elem.downField("act_number").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseOrganIssuer(elem: ACursor): Set[String] = {
    elem.downField("organ_issuer").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseSourceName(elem: ACursor): Set[String] = {
    elem.downField("source_name").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseOfficialEmenta(elem: ACursor): Set[String] = {
    elem.downField("official_ementa").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseUnofficialEmenta(elem: ACursor): Set[String] = {
    elem.downField("unofficial_ementa").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseContributors(elem: ACursor): Set[String] = {
    if (elem.succeeded) parseArrContrib(elem.downField("contributors").downArray, Set[String]())
    else Set[String]()
  }

  @tailrec
  private def parseArrContrib(elem: ACursor,
                              set: Set[String]): Set[String] = {
    if (elem.succeeded) {
      val text: String = elem.downField("text").as[String].getOrElse("")

      if (text.isEmpty) parseArrContrib(elem.right, set)
      else parseArrContrib(elem.right, set + text)
    } else set
  }

  private def parseExtension(elem: ACursor): Set[String] = {
    elem.downField("item_extension").as[String] match {
      case Right(extension) => Set[String](extension)
      case Left(_) => Set[String]()
    }
  }

  private def parseDescription(elem: ACursor): Set[String] = {
    elem.downField("description").as[String] match {
      case Right(description) => Set[String](description)
      case Left(_) => Set[String]()
    }
  }

  private def getMediaType(meta: Map[String,Set[String]]): MediaType = {
    meta.get("ur") match {
      case Some(ur) =>
        meta.get("mt") match {
          case Some(mt) => MediaType.fromString(mt.headOption.getOrElse(""))
          case None =>
            mtRecognizer.getMediaType(ur.headOption.getOrElse("")) match {
              case Right(mt) => mt
              case Left(_) => Other
            }
        }
      case None =>
        meta.get("mt").map(mt => MediaType.fromString(mt.headOption.getOrElse(""))).getOrElse(Other)
    }
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
        "\n\t[-docId=<id>] - update only one document whose id is <id>. if used, --onlyMissing parameter will be ignored" +
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