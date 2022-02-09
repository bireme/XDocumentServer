package org.bireme.xds.XDocServer

import scalaj.http.{Http, HttpOptions, HttpResponse}

import java.net.URL
import scala.collection.immutable.Set
import scala.util.{Failure, Success, Try}

sealed trait MediaType

object MediaType {
  def fromString(str: String): MediaType = {
    str.trim.toUpperCase match {
      case "PDF" => Pdf
      case "VIDEO" => Video
      case "AUDIO" => Audio
      case "PRESENTATION" => Presentation
      case "IMAGE" => Image
      case _ => Other
    }
  }
}

case object Pdf extends MediaType {
  override def toString: String = "pdf"
}
case object Video extends MediaType {
  override def toString: String = "video"
}
case object Audio extends MediaType {
  override def toString: String = "audio"
}
case object Presentation extends MediaType {
  override def toString: String = "presentation"
}
case object Image extends MediaType {
  override def toString: String = "image"
}
case object Other extends MediaType {
  override def toString: String = "link"
}

class MediaTypeRecognizer {
  val videos: Set[String] = Set("3g2", "3gp", "amv", "asf", "avi", "drc", "f4a", "f4b", "f4p", "f4v", "flv", "gif",
    "gifv", "m2ts", "m2v", "m4p", "m4v", "mkv", "mng", "mov", "mp2", "mp4", "mpe", "mpeg", "mpg", "mpv", "mts", "mxf",
    "nsv", "ogg", "ogv", "qt", "rm", "rmvb", "roq", "svi", "ts", "vob", "webm", "wmv", "yuv")
  val videoDomains: Set[String] = Set("coverr.co", "videezy.com", "videvo.net", "clipcanvas.com", "xstockvideo.com",
    "cutestockfootage.com", "ignitemotion.com", "footagecrate.com", "pexels.com", "pixabay.com", "vimeo.com", "youtube.com")

  val audios: Set[String] = Set("3gp", "8svx", "aa", "aac", "aax", "act", "aiff", "alac", "amr", "ape", "au", "awb",
    "cda", "dct", "dss", "dvf", "flac", "gsm", "ikla", "ivs", "m4a", "m4b", "m4p", "mmf", "mp3", "mpc", "msv", "nmf",
    "nsf", "ogg", "opus", "ra", "raw", "rf64", "sln", "tta", "voc", "vox", "wav", "webm", "wma", "wv")
  val audioDomais: Set[String] = Set("soundcloud.com", "deezer.com", "apple.com", "music.google.com",
    "gvtmusic.com.br", "mixrad.io", "napster.com", "rdio.com", "spotify.com", "tidal.com", "tunein.com",
    "music.xbox.com", "incompetech.com", "purple-planet.com", "soundimage.org", "bensound.com", "dig.ccmixter.org",
    "sampleswap.org", "newgrounds.com", "playonloop.com", "jamendo.com")

  val images: Set[String] = Set("jpg", "jpeg", "jpe", "jif", "jfif", "jfi", "png", "gif", "webp", "tiff", "tif",
    "psd", "raw", "arw", "cr2", "nrw", "k25", "bmp", "dib", "heif", "heic", "ind", "indd", "indt", "jp2", "j2k",
    "jpf", "jpx", "jpm", "mj2", "svg", "svgz", "ai", "eps")
  val imageDomais: Set[String] = Set("unsplash.com", "rawpixel.com", "freepik.es", "vintagestockphotos.com",
    "morguefile.com", "pixabay.com", "rgbstock.com", "stockvault.net", "deathtothestockphoto.com", "libreshot.com",
    "getrefe.com", "reshot.com", "offers.hubspot.com", "picjumbo.com", "nos.twnsnd.co", "focastock.com",
    "commons.wikimedia.org", "wellcomecollection.org", "public-domain-photos.com", "picdrome.com",
    "search.creativecommons.org", "imagebase.net", "insectimages.org", "creativity103.com", "usda.gov", "thestocks.im",
    "startupstockphotos.com", "pexels.com", "startupstockphotos.com", "magdeleine.co",
    "travelcoffeebook.com", "moveast.me", "thepatternlibrary.com", "publicdomainarchive.com", "foodiesfeed.com",
    "picography.co", "flickr.com", "jaymantri.com", "sitebuilderreport.com", "kaboompics.com", "isorepublic.com",
    "foter.com", "stocksnap.io", "skitterphoto.com", "focastock.com", "burst.shopify.com", "realisticshots.com",
    "bucketlistly.blog", "gratisography.com", "splitshire.com", "lifeofpix.com", "negativespace.co",
    "cupcake.nilssonlee.se", "epicantus.tumblr.com", "fancycrave.com", "albumarium.com", "flaticon.com",
    "pt.vecteezy.com", "temqueter.org", "canva.com")

  val presentations: Set[String] = Set("gslides", "key", "keynote", "nb", "nbp", "odp", "otp", "pez", "pot", "pps",
    "ppt", "pptx", "prz", "sdd", "shf", "show", "shw", "slp", "sspss", "sti", "sxi", "thmx", "watch")
  val presentationDomains: Set[String] = Set("slideshare.net", "www.slideshare.net")


  def getMediaType(urlStr: String): Either[String, MediaType] = {
    Try (new URL(urlStr)) match {
      case Success(url) =>
        val query: Option[String] = Option(url.getQuery)
        val extension: String = query match {
          case Some(qry) =>
            val dotLastIndex: Int = qry.lastIndexOf(".")
            if (dotLastIndex == -1) "" else qry.substring(dotLastIndex + 1)
          case None =>
            val pathT: String = url.getPath.trim.toLowerCase
            val dotLastIndex: Int = pathT.lastIndexOf(".")
            val slashLastIndex: Int = pathT.lastIndexOf("/")
            if ((dotLastIndex == -1) || (slashLastIndex > dotLastIndex)) ""
            else pathT.substring(dotLastIndex + 1)
        }

        extension match {
          case "" =>
            getMediaType(url) match {
              case Other =>
                Try {
                  val response: HttpResponse[String] = Http(urlStr).option(HttpOptions.followRedirects(true))
                    .timeout(connTimeoutMs = 5000, readTimeoutMs = 5000).asString
                  val headers: Map[String, IndexedSeq[String]] = response.headers

                  headers.getOrElse("Content-Type", Vector[String]()).headOption.getOrElse("")
                } match {
                  case Success(contentType) => Right(getContentType(contentType))
                  case Failure(ex) => Left(ex.toString)
                }
              case mt => Right(mt)
            }
          case "pdf" => Right(Pdf)
          case pdfPlus if pdfPlus.startsWith("pdf?") => Right(Pdf)  // For urls of type: https://apps.who.int/iris/bitstream/handle/10665/330851/9789240001381-eng.pdf?ua=1#.YgO_JcMrfdw.link
          case video if videos contains video => Right(Video)
          case audio if audios contains audio => Right(Audio)
          case presentation if presentations contains presentation => Right(Presentation)
          case image if images contains image => Right(Image)
          case _ => Right(Other)
        }
      case Failure(ex) => Left(ex.toString)
    }
  }

  private def getMediaType(url: URL): MediaType = {
    val domain: String = url.getHost

    if (videoDomains.exists(domain.contains)) Video
    else if (audioDomais.exists(domain.contains)) Audio
    else if(imageDomais.exists(domain.contains)) Image
    else if(presentationDomains.exists(domain.contains)) Presentation
    else Other
  }

  private def getContentType(contentType: String): MediaType = {
    contentType.toLowerCase match {
      case x if x.contains("pdf") => Pdf
      case x if x.contains("video") => Video
      case x if x.contains("audio") => Audio
      case x if x.contains("mspowerpoint") || x.contains("presentation") => Presentation
      case x if x.contains("image") => Image
      case _ => Other
    }
  }
}