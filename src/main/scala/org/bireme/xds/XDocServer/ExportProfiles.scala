 /*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{File, Writer}
import java.nio.charset.Charset
import java.nio.file.{Files, StandardOpenOption}

object ExportProfiles extends App {
  private def usage(): Unit = {
    System.err.println("usage: ExportProfiles <options>")
    System.err.println("\nOptions:")
    System.err.println("\t-pdfPath=<pdfPath> - path to pdfs Lucene index")
    System.err.println("\t-outFilePath=<outFilePath> - path to output export file")
    System.exit(1)
  }

  val parameters: Map[String, String] = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2)
        map + ((split(0).substring(1), split(1)))
      else {
        usage()
        map
      }
  }
  val pdfPath = parameters("pdfPath")
  val outFilePath = parameters("outFilePath")
  //val x = play.api.libs.json.JsString("")
  export (pdfPath, outFilePath)

  def export(pdfPath: String,
             outFilePath: String): Unit = {
    val docs: DocumentServer = new FSDocServer(new File(pdfPath), Some("pdf"))
    val writer: Writer = Files.newBufferedWriter(new File(outFilePath).toPath, Charset.forName("utf-8"),
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

    val infos: Map[String, Map[String, Set[String]]] =
      docs.getDocuments.foldLeft(Map[String, Map[String,Set[String]]]()) {
        case (map, id) =>
          println(s"Exporting doc_id=$id")
          docs.getDocumentInfo(id) match {
            case Right(info: Map[String, Set[String]]) =>
              map + (id -> info)
            case Left(exc) =>
              System.err.println(s"Export error. id=$id. errCode=${exc.toString}")
              map
          }
      }
    writer.append(InfoImpExp.map2Json(infos))
    writer.close()
  }
}
