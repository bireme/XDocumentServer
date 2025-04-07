package org.bireme.xds.XDocServer

import org.apache.solr.client.solrj.impl.Http2SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.{QueryResponse, UpdateResponse}
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import scalaj.http.{Http, HttpOptions}

import scala.jdk.CollectionConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter, seqAsJavaListConverter}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.security.SecureRandom
import java.util
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

object PostProcessing extends App {
  private def usage(): Unit = {
    System.out.println("usage: PostProcessing <coreUrl>")
    System.exit(-1)
  }

  if (args.length < 1) usage()
  val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  rootLogger.setLevel(Level.ERROR)

  var total = 0

  checkUrls(args(0)) match {
    case Success(_) =>
      println(s"total=$total")
      System.exit(0)
    case Failure(exception) =>
      exception.printStackTrace()
      System.exit(-1)
  }

  def checkUrls(coreUrl: String): Try[Unit] = {
    Try {
      val solrClient: Http2SolrClient = new Http2SolrClient.Builder(coreUrl).build()

      // Cria um trust manager que não faz validação de certificado
      val trustAllCerts: Array[TrustManager] = Array[TrustManager](new X509TrustManager() {
        def getAcceptedIssuers(): Null = null

        def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = {}

        def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = {}
      })
      // Configura o contexto SSL para usar o trust manager personalizado
      val sslContext: SSLContext = SSLContext.getInstance("SSL")
      sslContext.init(null, trustAllCerts, new SecureRandom())

      val rowsPerPage: Int = 100 // Number of documents retrieved each time
      val query: SolrQuery = new SolrQuery("*:*")
        .setSort("id", SolrQuery.ORDER.asc)  // To avoid repeated documents using query.setStart
        .setRows(rowsPerPage)
        .setFields("id", "ur") // Especifica que queremos apenas os campos "id" e "ur"

      processResults(solrClient, query, rowsPerPage, sslContext, 0)
      solrClient.commit()
      solrClient.close()
    }
  }

  @tailrec
  private def processResults(solrClient: Http2SolrClient,
                             query: SolrQuery,
                             rowsPerPage: Int,
                             sslContext: SSLContext,
                             start: Int): Unit = {
    query.setStart(start)
    val response: QueryResponse = solrClient.query(query)
    val results: Iterable[SolrDocument] = response.getResults.asScala
    val documents: ListBuffer[SolrInputDocument] = new scala.collection.mutable.ListBuffer[SolrInputDocument]()

    println(s"+++ $start")

    results.foreach {
      doc =>
        val id: String = doc.getFieldValue("id").asInstanceOf[String]
        val inputDoc: SolrInputDocument = new SolrInputDocument()
        inputDoc.addField("id", doc.getFieldValue("id")) // Mantém o mesmo ID

        val urlOk: Boolean = Option(doc.getFieldValue("ur")) match {
          case Some(ur) =>
            val urls: Iterable[String] = ur.asInstanceOf[util.ArrayList[String]].asScala
            urls.headOption match {
              case Some(url) =>
                validateUrl(url, sslContext) match {
                  case Success(code) =>
                    if (code < 400) {
                      println(s"id:$id url:$url OK!!!")
                      true
                    } else {
                      System.err.println(s"id:$id url:$url code:$code")
                      false
                    }
                  case Failure(exception) =>
                    System.err.println(s"id:$id url:$url exception=$exception")
                    false
                  }
              case None =>
                System.err.println(s"doc=$id does not have ur field")
                false
            }
          case None =>
            System.err.println(s"doc=$id does not have ur field")
            false
        }
        inputDoc.addField("urlOk", Map("set" -> urlOk).asJava) // Atualiza apenas o campo "urlOk" usando a operação "set"
        documents += inputDoc
    }

    val docsSize: Int = documents.length
    total += docsSize
    println(s"total written=$total  step written=$docsSize")
    if (docsSize > 0) {
      val response1: UpdateResponse = solrClient.add(documents.toList.asJava) // Converte a lista Scala para Java
      val exception1: Exception = response1.getException
      if (exception1 != null) throw exception1
      val response2: UpdateResponse = solrClient.commit() // Faz o commit das mudanças
      val exception2: Exception = response2.getException
      if (exception2 != null) throw exception2

      if (docsSize == rowsPerPage) processResults(solrClient, query, rowsPerPage, sslContext, start + rowsPerPage)
    }
  }

  private def validateUrl(url: String,
                          sslContext: SSLContext): Try[Int] = {
    Try {
      Http(url)
        .method("HEAD")
        .timeout(connTimeoutMs = 30000, readTimeoutMs = 30000) // Limite de tempo de 30 segundos
        .option(HttpOptions.followRedirects(true))
        .option(HttpOptions.sslSocketFactory(sslContext.getSocketFactory))
        .execute().code
    }
  }
}