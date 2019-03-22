/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{BufferedWriter, File, OutputStreamWriter}
import java.nio.charset.Charset
import java.nio.file.Files

import io.circe.parser.parse
import io.circe.{ACursor, Json, ParsingFailure}

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

object GenerUrls extends App{
  private def usage(): Unit = {
    System.err.println("Display all urls from documents described at FI_Admin")
    System.err.println("\nusage: GenerUrls")
    System.err.println("\t[-fiadminApi=<url>] - url of the FI-Admin service. Default is 'https://fi-admin.bvsalud.org/api'")
    System.err.println("\t[-community=<id> - community id. Default is all of them")
    System.err.println("\t[-outFile=<file> - path to the output file. Default is standard output")
    System.exit(1)
  }

  val parameters = args.foldLeft[Map[String, String]](Map()) {
    case (map, par) =>
      val split = par.split(" *= *", 2)

      split.size match {
        case 2 => map + (split(0).substring(1) -> split(1))
        case _ => usage(); map
      }
  }

  val fiadminApi = parameters.getOrElse("fiadminApi", "https://fi-admin.bvsalud.org/api")
  val community = parameters.get("community")
  val outFile = parameters.get("outFile")

  gener(fiadminApi, community, outFile)

  def gener(fiadminApi: String,
            community: Option[String],
            outFile: Option[String]): Unit = {
    val writer: BufferedWriter = outFile match {
      case Some(oFile) => Files.newBufferedWriter(new File(oFile).toPath, Charset.forName("utf-8"))
      case None => new BufferedWriter(new OutputStreamWriter(System.out))
    }
    val commuIds: Set[String] = community match {
      case Some(com) => Set(com)
      case None => getCommunityIds(fiadminApi).getOrElse(Set())
    }
    commuIds.foreach {
      comId =>
        getCollectionIds(comId).foreach {
          colIds: Set[String] =>
            colIds.foreach {
              colId: String =>
                val urls = getUrls(fiadminApi, colId)
                urls foreach {
                  case (id, url) =>
                    if (outFile.isDefined) println(s"${comId}_$comId|$id|$url")
                    writer.write(s"${comId}_$comId|$id|$url\n")
                }
            }
        }
    }
    writer.close()
    println("The urls generation has finished.")
  }

  private def getCommunityIds(fiadminApi: String): Option[Set[String]] = {
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

  private def getUrls(fiadminApi: String,
                      colId: String): Set[(String,String)] = {
    Try {
      val src = Source.fromURL(s"$fiadminApi/bibliographic/?collection=$colId&status=1&limit=0&format=json", "utf-8")
      val doc: Either[ParsingFailure, Json] = parse(src.getLines().mkString("\n"))
      src.close()
      doc
    } match {
      case Success(json) => json match {
        case Right(js: Json) => getUrls(js.hcursor.downField("objects").downArray.first, Set[(String, String)]())
        case Left(_) => Set[(String, String)]()
      }
      case Failure(_) => Set[(String, String)]()
    }
  }

  private def getUrls(elem: ACursor,
                      aux: Set[(String, String)]): Set[(String, String)] = {
    if (elem.succeeded) {
      val id: String = parseId(elem)

      parseDocUrl(elem) match {
        case Some(url) => getUrls(elem.right, aux + ((id, url)))
        case None => getUrls(elem.right, aux)
      }
    } else aux
  }

  private def parseId(elem: ACursor): String = {
    elem.downField("id").as[Int] match {
      case Right(id: Int) => id.toString
      case Left(_) => ""
    }
  }

  private def parseDocUrl(elem: ACursor): Option[String] =
    elem.downField("electronic_address").downArray.first.downField("_u").as[String].toOption
}
