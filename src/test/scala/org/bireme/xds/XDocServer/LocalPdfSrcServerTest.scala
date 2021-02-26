/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.{ByteArrayInputStream, File}
import java.util

import org.scalatest.flatspec.AnyFlatSpec

class LocalPdfSrcServerTest extends AnyFlatSpec {
  // id(issn), url, title, year
  val parameters: Set[(String, String, String, String)] = Set(
    ("1a", "http://repebis.upch.edu.pe/articulos/acta.med.per/v33n1/a5.pdf",
      "Factores de riesgo para el abandono del tratamiento de tuberculosis pulmonar sensible en un establecimiento de salud de atención primaria, Lima, Perú", "2016"),
    ("2a", "https://www.scielosp.org/article/ssm/content/raw/?resource_ssm_path=/media/assets/icse/v18s2/1807-5762-icse-18-s2-1389.pdf",
      "Participação popular nas ações de educação em saúde", "2014"),
    ("3a", "http://www.escoladesaude.pr.gov.br/arquivos/File/TEXTOS_CURSO_VIGILANCIA/capacitacao_e_atualizacao_em_geoprocessamento_em_saude_3.pdf",
      "Introdução à Estatística Espacial para a Saúde Pública ", "2007"),
    ("4a", "http://portalarquivos2.saude.gov.br/images/pdf/2016/agosto/25/GVS-online.pdf", "Guia de Vigilância em Saúde", "2016"),
    ("5a", "http://www.who.int/mental_health/policy/Livroderecursosrevisao_FINAL.pdf",
      "LIVRO DE RECURSOS DA OMS SOBRE SAÚDE MENTAL, DIREITOS HUMANOS E LEGISLAÇÃO", "2005")
  )

  //val solrUrl = "http://localhost:8989/solr/pdfs2"
  val solrUrl = "http://localhost:9293/solr/pdfs"
  val sds = new SolrDocServer(solrUrl)
  val dir = new File("pdfs2")
  val docServer = new FSDocServer(dir, Some("pdf"))
  //val docServer = new SwayDBServer(dir)
  val lpds = new LocalPdfDocServer(docServer)
  val lpss = new LocalPdfSrcServer(sds, lpds)

  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall { param =>
        val id = param._1
        lpds.getDocument(id) match {
          case Left(err) => err == 404
          case Right(is) =>
            is.close()
            lpss.deleteDocument(id) match {
              case 200 =>
                lpds.getDocument(id) match {
                  case Left(err) => err == 404
                  case Right(_)  => false
                }
              case 404 => true
              case _   => false
            }
        }
      }
    )
  }


  //createDocument(id: String,
  //               source: InputStream,
  //               info: Option[Map[String, Set[String]]] = None): Int

  "The local pdf search server" should "create 2 Lucene documents using createDocument (using inputStream)" in {
    assert(
      parameters.take(2).forall {
        param: (String, String, String, String) =>
          val fldNames: Set[String] = Set("id", "ur", "ti", "ud")
          val lst: Set[String] = param.productIterator.map(_.toString).toSet
          val info: Map[String, Set[String]] = fldNames.zip(lst).toMap.map(kv => kv._1 -> Set(kv._2))

          Tools.url2InputStream(param._2) exists {
            is =>
              val ret: Int = lpss.createDocument(param._1, is, Some(info))
              is.close()
              ret equals 201
          }
      }
    )
  }

  //createDocument(id: String,
  //               url: String,
  //               info: Option[Map[String, Set[String]]]): Int

  "The local pdf search server" should "create the other Lucene documents using createDocument (using url)" in {
    assert(
      parameters.drop(2).forall {
        param =>
          val fldNames: Set[String] = Set("id", "ur", "ti", "ud")
          val set: Set[String] = param.productIterator.map(_.toString).toSet
          val info: Map[String, Set[String]] = fldNames.zip(set).toMap.map(kv => kv._1 -> Set(kv._2))

          lpss.createDocument(param._1, param._2, Some(info)) equals 201
      }
    )
  }

  it should "list all document identifiers" in {
    assert {
      val oids: Set[String] = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lpss.getDocuments

      oids.forall (id => ids.contains(id))
    }
  }

  it should "show all document metadata" in {
    assert(
      parameters.forall {
        param =>
          val omap: Map[String, Set[String]] = Map("ur" -> Set(param._2.trim), "ti" -> Set(param._3.trim),
                                                   "ud" -> Set(param._4.trim))

          lpss.getDocumentInfo(param._1) exists {
            map =>
              omap.forall {
                kv =>
                  if (map.contains(kv._1)) {
                    map(kv._1) match {
                      case col: Iterable[String] => col.equals(kv._2)
                      case _ => false
                    }
                  } else false
              }
          }
      }
    )
  }

  it should "compare the bytes of the remote and the stored files" in {
    assert {
      parameters.forall {
        param =>
          lpss.getDocument(param._1) exists {
            is =>
              Tools.inputStream2Array(is) exists {
                arr =>
                  Tools.url2InputStream(param._2) exists {
                    is2 =>
                      Tools.inputStream2Array(is2) exists {
                        a2 =>
                          is2.close()
                          util.Arrays.hashCode(arr) == util.Arrays.hashCode(a2)
                      }
                  }
              }
          }
      }
    }
  }

  it should "replace the last document with the first" in {
    assert {
      lpss.getDocumentInfo("1a") match {
        case Right(info) =>
          lpss.getDocument("1a") match {
            case Right(is) =>
              val doc: Option[Array[Byte]] = Tools.inputStream2Array(is)
              is.close()
              doc exists {
                doc1 =>
                  lpss.replaceDocument("5a", new ByteArrayInputStream(doc1), Some(info))
                  lpss.getDocument("5a") match {
                    case Right(is2) =>
                      val doc2 = Tools.inputStream2Array(is2)
                      is2.close()
                      doc2 exists (doc3 => util.Arrays.equals(doc1, doc3))
                    case Left(_) => false
                  }
              }
            case Left(_) => false
          }
        case Left(_) => false
      }
    }
  }

  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall {
        param =>
          val id = param._1
          lpss.deleteDocument(id) match {
            case 200 => lpds.getDocument(id) match {
              case Left(404) => true
              case _ => false
            }
            case _ => false
          }
      }
    )
  }
}
