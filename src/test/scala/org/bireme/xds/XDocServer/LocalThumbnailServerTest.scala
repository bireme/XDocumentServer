/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec

class LocalThumbnailServerTest extends AnyFlatSpec {
  // id(issn), url
  val parameters: Set[(String, String)] = Set(
    ("1b", "http://www.scielosp.org/article/ssm/content/raw/?resource_ssm_path=/media/assets/icse/v18s2/1807-5762-icse-18-s2-1389.pdf"),
    ("2b", "https://www.scielosp.org/article/ssm/content/raw/?resource_ssm_path=/media/assets/icse/v18s2/1807-5762-icse-18-s2-1389.pdf"),
    ("3b", "http://www.escoladesaude.pr.gov.br/arquivos/File/TEXTOS_CURSO_VIGILANCIA/capacitacao_e_atualizacao_em_geoprocessamento_em_saude_3.pdf"),
    ("4b", "http://portalarquivos2.saude.gov.br/images/pdf/2016/agosto/25/GVS-online.pdf"),
    ("5b", "http://www.who.int/mental_health/policy/Livroderecursosrevisao_FINAL.pdf")
  )

  val pdir = new File("pdfs2")
  val tdir = new File("thumbnails")
  Tools.deleteDirectory(tdir)

  val pdfDocServer = new FSDocServer(pdir, Some("pdf"))
  //val pdfDocServer = new SwayDBServer(pdir)
  val lpds = new LocalPdfDocServer(pdfDocServer)
  val thumbDocServer = new FSDocServer(tdir, Some("jpg"))
  //val thumbDocServer = new SwayDBServer(tdir)
  val lts = new LocalThumbnailServer(thumbDocServer, Some(Right(lpds)))

  "The local thumbnail server" should "import some pdf files from internet using createDocument" in {
    assert {
      val ret0 = parameters.forall {
        param =>
          Tools.url2InputStream(param._2) exists {
            is =>
              val ret = lts.createDocument(param._1, is, Pdf, None)
              is.close()
              ret == 201
          }
      }
      println(s"teste1 ret=$ret0")
      ret0
    }
  }

  it should "list all document identifiers" in {
    assert {
      val oids = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lts.getDocuments
      val ret = oids.forall(id => ids.contains(id))
      println(s"teste2 ret=$ret")
      ret
    }
  }

  it should "show all document metadata" in {
    assert {
      val ret = parameters.forall {
        param =>
          lts.getDocumentInfo(param._1) match {
            case Right(_ /*map*/) => /*println(map);*/ true
            case Left(_) => false
          }
      }
      println(s"teste3 ret=$ret")
      ret
    }
  }

  it should "delete all documents and don't find then anymore" in {
    assert {
      val ret = parameters.forall {
        param =>
          val id = param._1
          lts.deleteDocument(id) match {
            case 200 => lts.getDocument(id, None) match {
              case Left(404) => true
              case _ => false
            }
            case _ => false
          }
      }
      println(s"teste4 ret=$ret")
      ret
    }
  }

  Tools.deleteDirectory(tdir)
  Tools.createDirectory(tdir)

  it should "import some pdf files from internet using getDocument" in {
    assert {
      val ret = parameters.forall {
        param =>
          val b = lts.getDocument(param._1, Some(param._2)).isRight
          println(s"teste5 b=$b url=${param._2}")
          b
      }
      println(s"teste5 ret=$ret")
      ret
    }
  }

  it should "import a repeated pdf file using getDocument" in {
    assert {
      val param = parameters.head
      val ret = lts.getDocument(param._1, Some(param._2)).isRight
      println(s"teste6 ret=$ret")
      ret
    }
  }

  it should "list all document identifiers - 2" in {
    assert {
      val oids = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lts.getDocuments
      val ret = oids.forall(id => ids.contains(id))
      println(s"teste7 ret=$ret")
      ret
    }
  }

  it should "show all document metadata - 2" in {
    assert {
      val ret = parameters.forall {
        param =>
          lts.getDocumentInfo(param._1) match {
            case Right(map) => println(map); true
            case Left(_) => println("erro - show all document metadata - 2"); false
          }
      }
      println(s"teste8 ret=$ret")
      ret
    }
  }
}
