/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.net.{URI, URL, URLConnection, URLDecoder}
import java.text.Normalizer
import java.text.Normalizer.Form

import scala.util.{Failure, Success, Try}

object Tools {
  def uniformString(in: String): String = {
    require (in != null)

    val s1 = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    s2.replaceAll("[^\\w\\-]", "")  // Hifen
  }

  /**
    * Convert an input stream into a byte array
    * @param is the input stream to be converted
    * @return an output byte array
    */
   def inputStream2Array(is: InputStream): Option[Array[Byte]] = {
    Try {
      val bos = new ByteArrayOutputStream()
      val buffer = Array.ofDim[Byte](2048)
      var continue = true

      while (continue) {
        val read = is.read(buffer)
  //println(s"inputStream2Array read=$read")
        if (read >= 0)
          bos.write(buffer, 0, read)
        else continue = false
      }

      bos.close()
      Some(bos.toByteArray)
    } match {
      case Success(array) => array
      case Failure(_) => None
    }
  }

  /**
    * Comvert an url into a input stream
    * @param url input url string
    * @return the output imput stream
    */
  def url2InputStream(url: String): Option[InputStream] = {
    val timeout = 4 * 60 * 1000

    Try {
      val url1 = new  URL(urlEncode(url))
      url1.getProtocol match {
        case "http" | "https" =>
          val conn: URLConnection = url1.openConnection()

          conn.setConnectTimeout(timeout)
          conn.setReadTimeout(timeout)
          conn.getInputStream
        case "ftp" => url1.openStream()
        case _ => throw new IllegalArgumentException("protocol")
      }
    } match {
      case Success(is) => Some(is)
      case Failure(_) =>
        println(s"--- Downloading error. url:$url")
        None
    }
  }

  /**
    * Convert an url into a byte array
    * @param url the input url string
    * @return the output byte array
    */
  def url2ByteArray(url: String): Option[Array[Byte]] = {
    url2InputStream(url).flatMap {
      is =>
        val arr: Option[Array[Byte]] = inputStream2Array(is)
        is.close()
        arr
    }
    /*val timeout = 4 * 60 * 1000

    Try(Http(url.toString).timeout(timeout, timeout).asBytes) match {
      case Success(response) =>
        val arr: Array[Byte] = response.body
        if (response.is2xx) Some(arr)
        else None
      case Failure(_) => None
    }*/
  }

  /**
    * Create a new file in a directory
    * @param directoryToBeCreated the path to the file
    * @return true if the file was created or false otherwise
    */
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

  /**
    * Encoding a url. See https://stackoverflow.com/questions/724043/http-url-address-encoding-in-java
    * @param surl input url string to encode
    * @return the encoded url
    */
  def urlEncode(surl: String): String = {
    val url = new URL(URLDecoder.decode(surl, "utf-8"))     // To avoid double encoding
    val uri = new URI(url.getProtocol, url.getUserInfo, url.getHost,
                      url.getPort, url.getPath, url.getQuery, url.getRef)
     uri.toURL.toString  // Do not treat # in the URLpath
  }
}
