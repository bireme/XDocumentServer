/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{File, InputStream}
import java.net.{URL, URLConnection}
import java.text.Normalizer
import java.text.Normalizer.Form

import scalaj.http.Http

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Tools {
  def uniformString(in: String): String = {
    require (in != null)

    val s1 = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    s2.replaceAll("[^\\w\\-]", "")  // Hifen
  }

  def inputStream2Array(is: InputStream): Option[Array[Byte]] = {
    Try {
      var continue = true
      val aux = Array.ofDim[Byte](1024*1024)
      val buffer = mutable.Buffer[Byte]()

      while (continue) {
        val read = is.read(aux)
        if (read == -1) continue = false
        else buffer ++= aux.take(read)
      }

      is.close()
      Some(buffer.toArray)
    } match {
      case Success(array) => array
      case Failure(_) => None
    }
  }

  def url2InputStream(url: URL): Option[InputStream] = {

    Try {
      val conn: URLConnection = url.openConnection()

      conn.setConnectTimeout(4 * 60 * 1000)
      conn.getInputStream
    } match {
      case Success(is) => Some(is)
      case Failure(_) =>
        println(s"--- Error downloading:${url.toString}")
        None
    }
  }

  def url2ByteArray(url: URL): Option[Array[Byte]] = {
    val timeout = 4 * 60 * 1000

    Try(Http(url.toString).timeout(timeout, timeout).asBytes) match {
      case Success(response) =>
        if (response.is2xx) Some(response.body)
        else None
      case Failure(_) => None
    }
  }

  def createDirectory(directoryToBeCreated: File): Boolean =
    (directoryToBeCreated == null) || directoryToBeCreated.mkdirs()

  def deleteDirectory(directoryToBeDeleted: File): Boolean = {
    val allContents = directoryToBeDeleted.listFiles
    if (allContents != null) allContents.foreach(file => deleteDirectory(file))
    directoryToBeDeleted.delete
  }

  def map2String(map: Map[String,Seq[String]]): String = {
    map.foldLeft("") {
      case (str, kv) => kv._2.foldLeft(str) {
        case (str2, value) => str2 + (if (str.isEmpty) "" else "ºº") + s"${kv._1.trim}=${value.trim}"
      }
    }
  }

  def string2Map(str: String): Map[String, Seq[String]] = {
    str.split("ºº").foldLeft(Map[String, Seq[String]]()) {
      case (mp, x) =>
        val split: Array[String] = x.split("=")
        val key: String = split(0)
        val seq: Seq[String] = mp.getOrElse(key, Seq[String]())
        mp + (key -> (seq :+ split(1)))
    }
  }
}
