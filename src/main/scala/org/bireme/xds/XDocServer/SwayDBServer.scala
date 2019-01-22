/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io._
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import swaydb.SwayDB
import swaydb.data.config.Dir
import swaydb.serializers.Default._

import scala.util.{Failure, Success}

class SwayDBServer(dbDir: File) extends DocumentServer {
  val db: swaydb.Map[String, Array[Byte]] = SwayDB.persistent[String, Array[Byte]](
    dir = new File(dbDir, "disk1").toPath,
    otherDirs = Seq(Dir(new File(dbDir, "disk2").toPath, distributionRatio = 2))).get
  val dbInfo: swaydb.Map[String, String] = SwayDB.persistent[String, String](
    dir = new File(dbDir, "disk1_info").toPath,
    otherDirs = Seq(Dir(new File(dbDir, "disk2_info").toPath, distributionRatio = 2))).get

  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  override def getDocuments: Set[String] = {
    db.keys.to[Set]
    //db.keys.to[Set[String]]
  }

  /**
    * Retrieve a stored document
    *
    * @param id document identifier
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  override def getDocument(id: String): Either[Int, InputStream] = {
    db.get(id.trim) match {
      case Success(arrOpt) => arrOpt match {
        case Some(arr) => Right(new ByteArrayInputStream(arr))
        case None      => Left(404)
      }
      case Failure(_) => Left(500)
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document content
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              source: InputStream,
                              info: Option[Map[String, Seq[String]]] = None): Int = {
    val idT: String = id.trim

    db.contains(idT) match {
      case Success(value) =>
        if (value) 409
        else {
          Tools.inputStream2Array(source) match {
            case Some(arr) =>
              db.put(idT, arr) match {
                case Success(_) => info match {
                  case Some(inf) =>
                    dbInfo.put(idT, Tools.map2String(inf)) match {
                      case Success(_) => 201
                      case Failure(_) => 500
                    }
                  case None => 201
                }
                case Failure(_) => 500
              }
            case None => 500
          }
        }
      case Failure(_) => 500
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
                              info: Option[Map[String, Seq[String]]]): Int = {
    val idT: String = id.trim

    db.contains(idT) match {
      case Success(value) =>
        if (value) 409
        else {
          Tools.url2ByteArray(url) match {
            case Some(arr) =>
              db.put(idT, arr) match {
                case Success(_) =>
                  val inf = info.getOrElse(createDocumentInfo(idT, source = None, info = None) + ("url" -> Seq(url)))
                  dbInfo.put(idT, Tools.map2String(inf)) match {
                    case Success(_) => 201
                    case Failure(_) => 500
                  }
                case Failure(_) => 500
              }
            case None => 500
          }
        }
      case Failure(_) => 500
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source the source of the document content
    * @param info metainfo of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               source: InputStream,
                               info: Option[Map[String, Seq[String]]] = None): Int = {
    deleteDocument(id) match {
      case 200 =>
        createDocument(id, source, info) match {
          case 201 => 200
          case err => err
        }
      case _ => 500
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               url: String,
                               info: Option[Map[String, Seq[String]]]): Int = {
    deleteDocument(id) match {
      case 200 =>
        createDocument(id, url, info) match {
          case 201 => 200
          case err => err
        }
      case _ => 500
    }
  }

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  override def deleteDocument(id: String): Int = {
    val idT: String = id.trim

    db.contains(idT) match {
      case Success(value) =>
        if (value) {
          db.remove(idT) match {
            case Success(_) =>
              dbInfo.remove(idT) match {
                case Success(_) => 200
                case Failure(_) => 500
              }
            case Failure(_) => 500
          }
        } else 404
      case Failure(_) => 500
    }
  }

  /**
    * Delete all stored documents
    * @return a http error code. 200 (ok) or or 500 (internal server error)
    */
  override def deleteDocuments(): Int = {
    val docIds = getDocuments

    if (docIds.isEmpty) 200
    else {
      db.batchRemove(docIds) match {
        case Success(_) => 200
        case Failure(_) => 500
      }
    }
  }

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  override def getDocumentInfo(id: String): Either[Int, Map[String, Seq[String]]] = {
    dbInfo.get(id.trim) match {
      case Success(strOpt) =>
        strOpt match {
          case Some(str) => Right(Tools.string2Map(str))
          case None      => Left(404)
        }
      case Failure(_) => Left(500)
    }
  }

  /**
    * Create a metadata for the document
    * @param id document identifier (document id from FI Admin)
    * @param source source of the document content
    * @param info other metadata source
    * @return the document metadata
    */
  override def createDocumentInfo(id: String,
                                  source: Option[InputStream] = None,
                                  info: Option[Map[String, Seq[String]]] = None): Map[String, Seq[String]] = {
    val now: Date = Calendar.getInstance().getTime
    val dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
    val date: String = dateFormat.format(now)

    Map("id" -> Seq(id), "date" -> Seq(date)) ++ info.getOrElse(Map[String, Seq[String]]())
  }
}
