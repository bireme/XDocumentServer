/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.File
import java.util

import org.scalatest.flatspec.AnyFlatSpec

class LocalPdfDocServerTest extends AnyFlatSpec {
  // id(issn), url, title, year
  val parameters: Set[(String, String, String, String)] = Set(
    ("1", "http://repebis.upch.edu.pe/articulos/acta.med.per/v33n1/a5.pdf",
    "Factores de riesgo para el abandono del tratamiento de tuberculosis pulmonar sensible en un establecimiento de salud de atención primaria, Lima, Perú", "2016"),
    ("2", "https://www.scielosp.org/article/ssm/content/raw/?resource_ssm_path=/media/assets/icse/v18s2/1807-5762-icse-18-s2-1389.pdf",
      "Participação popular nas ações de educação em saúde", "2014"),
    ("3", "http://www.escoladesaude.pr.gov.br/arquivos/File/TEXTOS_CURSO_VIGILANCIA/capacitacao_e_atualizacao_em_geoprocessamento_em_saude_3.pdf",
      "Introdução à Estatística Espacial para a Saúde Pública ", "2007"),
    ("4", "http://portalarquivos2.saude.gov.br/images/pdf/2016/agosto/25/GVS-online.pdf", "Guia de Vigilância em Saúde", "2016"),
    ("5", "http://www.who.int/mental_health/policy/Livroderecursosrevisao_FINAL.pdf",
      "LIVRO DE RECURSOS DA OMS SOBRE SAÚDE MENTAL, DIREITOS HUMANOS E LEGISLAÇÃO", "2005")
  )

  val dir: File = new File("pdfs2")
  Tools.deleteDirectory(dir)
  val docServer = new FSDocServer(dir, Some("pdf"))
  //val docServer = new SwayDBServer(dir)
  val lpds = new LocalPdfDocServer(docServer)

  "The local pdf doc server" should "import some pdf files from internet using createDocument" in {
    assert(
      parameters.forall {
        param =>
          Tools.url2InputStream(param._2) exists {
            is =>
              val map = Map("id" -> Set(param._1), "url" -> Set(param._2), "title" -> Set(param._3), "year" -> Set(param._4))
              val ret = lpds.createDocument(param._1, is, Some(map))
              is.close()
              val ok = ret == 201
              if (!ok) {
                println(s"ERROR: url=${param._2} ret=$ret")
              }
              ok
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
          val omap: Map[String, Set[String]] = Map("id" -> Set(param._1.trim), "url" -> Set(param._2.trim), "title" ->
            Set(param._3.trim), "year" -> Set(param._4.trim))
          lpds.getDocumentInfo(param._1) exists {
            map =>
              (map.toSet diff omap.toSet).isEmpty
          }
      }
    )
  }

  it should "compare the bytes of the remote and the stored files" in {
    assert(
      parameters.forall {
        param => lpds.getDocument(param._1) exists {
          is => Tools.inputStream2Array(is) exists {
            arr => Tools.url2InputStream(param._2) exists {
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
          val map = Map("id" -> Set(param._1), "url" -> Set(param._2), "title" -> Set(param._3), "year" -> Set(param._4))
          lpds.getDocument(param._1, Some(param._2), Some(map)).isRight
      }
    )
  }

  it should "import a repeated pdf file using getDocument" in {
    assert {
      val param: (String, String, String, String) = parameters.head
      val map = Map("id" -> Set(param._1), "url" -> Set(param._2), "title" -> Set(param._3), "year" -> Set(param._4))
      lpds.getDocument(param._1, Some(param._2), Some(map)).isRight
    }
  }

  it should "list all document identifiers - 2" in {
    assert {
      val oids = parameters.foldLeft(Set[String]()) {
        case (seq, param) => seq + param._1
      }
      val ids = lpds.getDocuments
      oids.forall(id => ids.contains(id))
    }
  }

  it should "show all document metadata - 2" in {
    assert(
      parameters.forall {
        param =>
          val omap = Map("id" -> Set(param._1.trim), "url" -> Set(param._2.trim), "title" -> Set(param._3.trim), "year" -> Set(param._4.trim))
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
                is.close()
                Tools.url2InputStream(param._2) exists {
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
