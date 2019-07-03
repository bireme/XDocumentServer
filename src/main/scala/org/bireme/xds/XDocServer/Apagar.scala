import java.net.{HttpURLConnection, URL, URLDecoder, URLEncoder}

object UrlContent extends App {
  def connect(urls: String): Int = {
    val decoded = URLDecoder.decode(urls, "utf-8")
    val encoded = encode(decoded, "utf-8")
    println(s"    url=$urls\ndecoded=$decoded\nencoded=$encoded")
    val url = new URL(encoded)
    val conn: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]

    conn.getResponseCode
  }

  def encode(urls: String,
             encod: String): String = {
    val url = new URL(urls)
    val protocol = url.getProtocol
    val authority = url.getAuthority
    val path = encodePath(url.getPath, encod)
    val query = url.getQuery
    val query2 = if (query == null) "" else s"?${encodeQuery(query, encod)}"
    val fragment = url.getRef
    val fragment2 = if (fragment == null) "" else s"#${encodeFragment(fragment, encod)}"

    protocol + "://" + authority + path + query2 + fragment2
  }

  def encodePath(path: String,
                 encod: String): String = {
    val split = path.split("/", 100)

    split.map(URLEncoder.encode(_, encod)).map(_.replace("+", "%20")).mkString("/")
  }

  def encodeQuery(query: String,
                  encod: String): String = { // xxx=yyy&www=zzz
    def encodeQueryPart(part: String): String = {  // xxx=yyy
      val split = part.split("=")
      if (split.length != 2) throw new IllegalArgumentException(part)

      split.map(URLEncoder.encode(_, encod)).mkString("=")
    }

    val amp = query.split("&")

    amp.map(encodeQueryPart).mkString("&")
  }

  def encodeFragment(fragment: String,
                     encod: String): String = {
    URLEncoder.encode(fragment, encod).replace("+", "%20")
  }

  val urls1 = "https://www.ins.gob.pe/insvirtual/images/otrpubs/pdf/ponzo%C3%B1osos.pdf"
  val urls2 = "http://www.ins.gob.pe/insvirtual/images/otrpubs/pdf/ponzoñosos.pdf"
  val urls3 = "http://www.ins.gob.pe/insvirtual/images/otrpubs/pdf/FINAL%20MALARIA%2028.12.10%5B1%5D.pdf"
  val urls4 = "http://www.ins.gob.pe/insvirtual/images/otrpubs/pdf/Normas_Guias_Indigenas_final.pdf"

  println(connect(urls1))
  println(connect(urls2))
  println(connect(urls3))
  println(connect(urls4))
}