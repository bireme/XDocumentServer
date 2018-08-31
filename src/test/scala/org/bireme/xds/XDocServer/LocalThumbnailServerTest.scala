package org.bireme.xds.XDocServer

import java.io.File
import java.net.URL

import org.scalatest.FlatSpec

class LocalThumbnailServerTest extends FlatSpec {
  // id(issn), url
  val parameters = Set(
   // ("1677-7042", "http://pesquisa.in.gov.br/imprensa/jsp/visualiza/index.jsp?jornal=1&pagina=68&data=22/09/2017"),
    ("978-85-334-1939-1", "http://189.28.128.100/dab/docs/publicacoes/geral/pnab.pdf"),
    ("978-85-334-1911-7", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_alimentacao_nutricao.pdf"),
    ("78-85-334-2146-2", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_praticas_integrativas_complementares_2ed.pdf"),
    ("no_issn", "http://189.28.128.100/dab/docs/publicacoes/geral/diretrizes_da_politica_nacional_de_saude_bucal.pdf")
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
