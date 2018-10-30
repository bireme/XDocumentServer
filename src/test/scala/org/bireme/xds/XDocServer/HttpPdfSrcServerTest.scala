/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import org.scalatest.FlatSpec
import scalaj.http.{Http, HttpResponse}

import scala.util.{Failure, Success, Try}

class HttpPdfSrcServerTest  extends FlatSpec {
  // id(issn), url, title, year
  val parameters = Set(
    ("1", "http://www.saude.pr.gov.br/arquivos/File/0SEGURANCA_DO_PACIENTE/modulo2.pdf",
      "Critérios Diagnósticos de Infecção Relacionada à Assistência à Saúde", "2013"),
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

  val hpssUrl = "http://localhost:9191/pdfSrcServer"

  "The http pdf search server" should "index some pdf docs and create its thumbnails using /putDocument" in {
    assert(
      parameters.forall {
        param =>
          Try {
            val params = Map(
              "id" -> param._1,
              "ur" -> param._2,
              "ti" -> param._3,
              "ud" -> param._4
            )
            val response: HttpResponse[String] = Http(hpssUrl + "/putDocument").params(params.toSeq).asString
            response.is2xx
          } match {
            case Success(boo) => boo
            case Failure(err) => false
          }
      }
    )
  }
/*
  it should "has the same metadata" in {
    assert(
      parameters.forall {
        param =>
          Try {
            val url = s"$hpssUrl/infoDocument?id=${param._1}"
            val response: HttpResponse[String] = Http(url).asString
            if (response.is2xx) {
              val parseResult = parse(response.body)
println(s"parseResult=$parseResult")
              true
            } else false
          } match {
            case Success(boo) => boo
            case Failure(_)   => false
          }
      }
    )
  } */
}
