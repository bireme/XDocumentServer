package org.bireme.xds.XDocServer

import java.io.File
import java.net.URL
import java.util

import org.scalatest.FlatSpec

class LocalPdfDocServerTest extends FlatSpec {
  // id(issn), url, title, year
  val parameters = Set(
    ("1677-7042", "http://pesquisa.in.gov.br/imprensa/jsp/visualiza/index.jsp?jornal=1&pagina=68&data=22/09/2017",
     "Politica Nacional de Atenção Básica (2017/PORTARIA)", "2017"),
    ("978-85-334-1939-1", "http://189.28.128.100/dab/docs/publicacoes/geral/pnab.pdf",
      "Política Nacional de Atenção Básica (2012)", "2012"),
    ("978-85-334-1911-7", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_alimentacao_nutricao.pdf",
     "Política Nacional de Alimentação e Nutrição", "2012"),
    ("78-85-334-2146-2", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_praticas_integrativas_complementares_2ed.pdf",
     "Política Nacional de Práticas Integrativas e Complementares no SUS - PNPIC-SUS: atitude de ampliação de acesso", "2006"),
    ("no_issn", "http://189.28.128.100/dab/docs/publicacoes/geral/diretrizes_da_politica_nacional_de_saude_bucal.pdf",
     "Diretrizes da política nacional de saúde bucal", "2004")
  )

  val dir: File = new File("pdfs2")
  Tools.deleteDirectory(dir)
  val lpds = new LocalPdfDocServer(new FSDocServer(dir))

  "The local pdf doc server" should "import some pdf files from internet using createDocument" in {
    assert(
      parameters.forall {
        param =>
          Tools.url2InputStream(new URL(param._2)) exists {
            is =>
              val map = Map("id" -> Seq(param._1), "url" -> Seq(param._2), "title" -> Seq(param._3), "year" -> Seq(param._4))
              val ret = lpds.createDocument(param._1, is, Some(map))
              is.close()
              ret == 201
          }
      }
    )
  }

  it should "list all document identifiers" in {
    assert {
      val oids: Set[String] = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids: Set[String] = lpds.getDocuments

      oids.forall(id => ids.contains(id))
    }
  }

  it should "show all document metadata" in {
    assert(
      parameters.forall {
        param =>
          val omap = Map("id" -> Seq(param._1), "url" -> Seq(param._2), "title" -> Seq(param._3), "year" -> Seq(param._4))
          lpds.getDocumentInfo(param._1) exists (map => map.equals(omap))
      }
    )
  }

  it should "compare the bytes of the remote and the stored files" in {
    assert(
      parameters.forall {
        param => lpds.getDocument(param._1) exists {
          is => Tools.inputStream2Array(is) exists {
            arr => Tools.url2InputStream(new URL(param._2)) exists {
              is2 => Tools.inputStream2Array(is2) exists {
                a2 =>
                  is2.close()
                  util.Arrays.hashCode(arr) == util.Arrays.hashCode(a2)
              }
            }
          }
        }
      }
    )
  }

  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall {
        param =>
          val id = param._1
          lpds.deleteDocument(id) match {
            case 200 => lpds.getDocument(id) match {
              case Left(404) => true
              case _ => false
            }
            case _ => false
          }
      }
    )
  }

  Tools.deleteDirectory(dir)
  Tools.createDirectory(dir)

  it should "import some pdf files from internet using getDocument" in {
    assert(
      parameters.forall {
        param =>
          val map = Map("id" -> Seq(param._1), "url" -> Seq(param._2), "title" -> Seq(param._3), "year" -> Seq(param._4))
          lpds.getDocument(param._1, Some(param._2), Some(map)).isRight
      }
    )
  }

  it should "import a repeated pdf file using getDocument" in {
    assert {
      val param: (String, String, String, String) = parameters.head
      val map = Map("id" -> Seq(param._1), "url" -> Seq(param._2), "title" -> Seq(param._3), "year" -> Seq(param._4))
      lpds.getDocument(param._1, Some(param._2), Some(map)).isRight
    }
  }

  it should "list all document identifiers - 2" in {
    assert {
      val oids = parameters.foldLeft(Seq[String]()) {
        case (seq, param) => seq :+ param._1
      }
      val ids = lpds.getDocuments
      oids.forall(id => ids.contains(id))
    }
  }

  it should "show all document metadata - 2" in {
    assert(
      parameters.forall {
        param =>
          val omap = Map("id" -> Seq(param._1), "url" -> Seq(param._2), "title" -> Seq(param._3), "year" -> Seq(param._4))
          lpds.getDocumentInfo(param._1) exists(map => map.equals(omap))
      }
    )
  }

  it should "compare the bytes of the remote and the stored files - 2" in {
    assert(
      parameters.forall {
        param =>
          lpds.getDocument(param._1) exists {
            is => Tools.inputStream2Array(is) exists {
              arr =>
                Tools.url2InputStream(new URL(param._2)) exists {
                  is2 => Tools.inputStream2Array(is2) exists {
                    a2 =>
                      is2.close()
                      util.Arrays.hashCode(arr) == util.Arrays.hashCode(a2)
                  }
                }
            }
          }
      }
    )
  }
}
