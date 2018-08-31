package org.bireme.xds.XDocServer

import java.io.File

import org.scalatest.FlatSpec

class LocalPdfSrcServerTest extends FlatSpec {
  // id(issn), url, title, year
  val parameters = Seq(
    //("1677-7042", "http://pesquisa.in.gov.br/imprensa/jsp/visualiza/index.jsp?jornal=1&pagina=68&data=22/09/2017",
    //  "Politica Nacional de Atenção Básica (2017/PORTARIA)", "2017"),
    ("817107", "http://bvsms.saude.gov.br/bvs/publicacoes/cadernos_atencao_basica_33.pdf",
      "Política Nacional de Atenção Básica (2012)", "2012"),
    ("978-85-334-1939-1", "http://189.28.128.100/dab/docs/publicacoes/geral/pnab.pdf",
      "Política Nacional de Atenção Básica (2012)", "2012"),
    ("978-85-334-1911-7", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_alimentacao_nutricao.pdf",
      "Política Nacional de Alimentação e Nutrição", "2012"),
    ("78-85-334-2146-2", "http://bvsms.saude.gov.br/bvs/publicacoes/politica_nacional_praticas_integrativas_complementares_2ed.pdf",
      "Política Nacional de Práticas Integrativas e Complementares no SUS - PNPIC-SUS: atitude de ampliação de acesso", "2006"),
    ("no_issn", "http://189.28.128.100/dab/docs/publicacoes/geral/diretrizes_da_politica_nacional_de_saude_bucal.pdf",
      "Diretrizes da política nacional de saúde bucal", "2004")
  )
  val solrUrl = "http://localhost:8983/solr/pdfs2"
  val sds = new SolrDocServer(solrUrl)
  val dir = new File("pdfs2")
  val lpds = new LocalPdfDocServer(new FSDocServer(dir))
  val lpss = new LocalPdfSrcServer(sds, Right(lpds))
/*
  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall {
        param =>
          val id = param._1
          lpss.deleteDocument(id) match {
            case 200 => lpds.getDocument(id) match {
              case Left(404) => println("400");true
              case _ => println("outro");false
            }
            case _ => println("delete error ");false
          }
      }
    )
  }

  //createDocument(id: String,
  //               source: InputStream,
  //               info: Option[Map[String, Seq[String]]] = None): Int

  "The local pdf search server" should "create 2 Lucene documents using createDocument (using inputStream)" in {
    assert(
      parameters.take(2).forall {
        param: (String, String, String, String) =>
          val fldNames: Seq[String] = Seq("id", "ur", "ti", "updated_date")
          val lst: Seq[String] = param.productIterator.toList.map(_.toString)
          val info: Map[String, Seq[String]] = fldNames.zip(lst).toMap.map(kv => kv._1 -> Seq(kv._2))
//println(s"info=$info")
          Tools.url2InputStream(new URL(param._2)) exists {
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
  //               info: Option[Map[String, Seq[String]]]): Int

  "The local pdf search server" should "create the other Lucene documents using createDocument (using url)" in {
    assert(
      parameters.drop(2).forall {
        case param =>
          val fldNames: Seq[String] = Seq("id", "ur", "ti", "updated_date")
          val lst: Seq[String] = param.productIterator.toList.map(_.toString)
          val info: Map[String, Seq[String]] = fldNames.zip(lst).toMap.map(kv => kv._1 -> Seq(kv._2))
          //println(s"info=$info")
            lpss.createDocument(param._1, param._2, Some(info)) equals 201
      }
    )
  }
/*
  it should "list all document identifiers" in {
    assert {
      val oids: Set[String] = parameters.foldLeft(Set[String]()) {
        case (set, param) => set + param._1
      }
      val ids = lpss.getDocuments
//println(s"ids=$ids")
      oids.forall (id => bool && ids.contains(id))
    }
  }

  it should "show all document metadata" in {
    assert(
      parameters.forall {
        param =>
          val omap: Map[String, Set[String]] = Map("ur" -> Set(param._2), "ti" -> Set(param._3), "updated_date" -> Set(param._4))

          lpss.getDocumentInfo(param._1) exists(map => omap.forall(kv => map.contains(kv._1) && map(kv._1).equals(kv._2)))
      }
    )
  }
 */

 /* it should "compare the bytes of the remote and the stored files" in {
    assert(
      parameters.forall {
        param =>
          lpss.getDocument(param._1) exists {
            is => Tools.inputStream2Array(is) exists {
              arr =>
                Tools.url2InputStream(new URL(param._2)) exists {
                  is2 =>
                    Tools.inputStream2Array(is2) exists {
                      a2 =>
                        is2.close()
                        println(s"arr1=${arr.mkString(",")}")
                        println(s"arr2=${a2.mkString(",")}")
                        Arrays.hashCode(arr) == Arrays.hashCode(a2)
                    }
                }
            }
          }
      }
    )
  }
*/
  it should "delete all documents and don't find then anymore" in {
    assert(
      parameters.forall {
        param =>
          val id = param._1
          lpss.deleteDocument(id) match {
            case 200 => lpds.getDocument(id) match {
              case Left(404) => println("400");true
              case _ => println("outro");false
            }
            case _ => println("delete error ");false
          }
      }
    )
  }*/
}
