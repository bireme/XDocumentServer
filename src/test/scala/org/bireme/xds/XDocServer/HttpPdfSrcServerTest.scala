package org.bireme.xds.XDocServer
import org.scalatest.FlatSpec

class HttpPdfSrcServerTest  extends FlatSpec {
  // id(issn), url, title, year
  /*val parameters = Set(
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

  val lpssUrl = "http://localhost:9191/putDocument"
  val solrUrl = "http://localhost:8983/solr/testeX"
  val sds = new SolrDocServer(solrUrl)
  val dir = new File("pdfsX")
  val lpds = new LocalPdfDocServer(new FSDocServer(dir))
  val lpss = new LocalPdfSrcServer(sds, Right(lpds))
  val hpss = new HttpPdfSrcServer(lpss)


  "The http pdf search server" should "index some pdf docs and create its thumbnails using /putDocument" in {
    assert(
      parameters.forall {
        param =>
          Try {
            val params = Map(
              "id" -> param._1,
              "url" -> param._2,
              "title" -> param._3,
              "year" -> param._4
            )
            val response: HttpResponse[String] = Http(lpssUrl).params(params).asString
            response.is2xx
          } match {
            case Success(boo) => boo
            case Failure(_)   => false
          }
      }
    )
  }*/
}
