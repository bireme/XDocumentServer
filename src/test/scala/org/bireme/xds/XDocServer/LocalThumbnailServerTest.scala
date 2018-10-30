/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File
import java.net.URL

import org.scalatest.FlatSpec

class LocalThumbnailServerTest extends FlatSpec {
  // id(issn), url
  val parameters = Set(
    ("1", "http://www.saude.pr.gov.br/arquivos/File/0SEGURANCA_DO_PACIENTE/modulo2.pdf"),
    ("2", "https://www.scielosp.org/article/ssm/content/raw/?resource_ssm_path=/media/assets/icse/v18s2/1807-5762-icse-18-s2-1389.pdf"),
    ("3", "http://www.escoladesaude.pr.gov.br/arquivos/File/TEXTOS_CURSO_VIGILANCIA/capacitacao_e_atualizacao_em_geoprocessamento_em_saude_3.pdf"),
    ("4", "http://portalarquivos2.saude.gov.br/images/pdf/2016/agosto/25/GVS-online.pdf"),
    ("5", "http://www.who.int/mental_health/policy/Livroderecursosrevisao_FINAL.pdf")
  )

  val pdir = new File("pdfs2")
  val tdir = new File("thumbnails")
  Tools.deleteDirectory(tdir)

  val lpds = new LocalPdfDocServer(new FSDocServer(pdir))
  val lts = new LocalThumbnailServer(new FSDocServer(tdir), Right(lpds))

  "The local thumbnail server" should "import some pdf files from internet using createDocument" in {
    assert(
      parameters.forall {
        param =>
          Tools.url2InputStream(new URL(param._2)) exists {
            is =>
              val ret = lts.createDocument(param._1, is)
              is.close()
              ret == 201
          }
      }
    )
  }

  it should "list all document identifiers" in {
    assert {
      val oids = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lts.getDocuments
      oids.forall(id => ids.contains(id))
    }
  }

  it should "show all document metadata" in {
    assert(
      parameters.forall {
        param =>
          lts.getDocumentInfo(param._1) match {
            case Right(map) => println(map); true
            case Left(_) => false
          }
      }
    )
  }

  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall {
        param =>
          val id = param._1
          lts.deleteDocument(id) match {
            case 200 => lts.getDocument(id) match {
              case Left(404) => true
              case _ => false
            }
            case _ => false
          }
      }
    )
  }

  Tools.deleteDirectory(tdir)
  Tools.createDirectory(tdir)

  it should "import some pdf files from internet using getDocument" in {
    assert(parameters.forall(param => lts.getDocument(param._1, Some(param._2)).isRight))
  }

  it should "import a repeated pdf file using getDocument" in {
    assert {
      val param = parameters.head
      lts.getDocument(param._1, Some(param._2)).isRight
    }
  }

  it should "list all document identifiers - 2" in {
    assert {
      val oids = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lts.getDocuments
      oids.forall(id => ids.contains(id))
    }
  }

  it should "show all document metadata - 2" in {
    assert(
      parameters.forall {
        param =>
          lts.getDocumentInfo(param._1) match {
            case Right(map) => println(map); true
            case Left(_) => println("erro - show all document metadata - 2");false
          }
      }
    )
  }
}
