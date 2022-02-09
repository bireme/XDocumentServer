package org.bireme.xds.XDocServer

import scala.collection.immutable.Set

object ShowMetadata extends App {
  private def usage(): Unit = {
    System.err.println(
      "\nusage: ShowMetadata [OPTIONS]" +
        "\n\nOPTIONS" +
        "\n\t-pdfDocDir=<dir> - directory where the pdf files will be stored" +
        "\n\t-thumbDir=<dir> - directory where the thumbnail files will be stored" +
        "\n\t-decsPath=<path> - decs master file path" +
        "\n\t-solrColUrl=<url> - solr collection url. For ex: http://localhost:8983/solr/pdfs",
      "\n\t-docId=<id> - document id to used to show its metadata"
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
  val keys = parameters.keys.toSet
  if (!Set("pdfDocDir", "thumbDir", "decsPath", "solrColUrl", "docId").forall(keys.contains)) usage()

  val updDoc = new UpdateDocuments(parameters("pdfDocDir"), parameters("solrColUrl"),
    parameters("thumbDir"), parameters("decsPath"), None)

  println(s"[FI-Admin API: ${updDoc.fiadminApi}]\n")

  updDoc.getMetadata(parameters("docId")) match {
    case Right(meta) => meta.foreach {
      case (k,v) => println(s"$k=${v.map(_.trim).mkString(" _||_ ")}")
    }
    case Left(exception) => println(s"Error during the metadata getting. Mgs:$exception")
  }
}
