package org.bireme.xds.XDocServer

import collection.JavaConverters._
import java.io.File

import org.apache.lucene.index.{DirectoryReader, IndexReader, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery, TopDocs}
import org.apache.lucene.store.FSDirectory

import scala.util.{Failure, Success, Try}

object IndexTest extends App {
  private def usage(): Unit = {
    Console.err.println("usage: IndexTest\n" +
      "\t\t<indexPath> - path to Lucene index\n" +
      "\t\t[<field>] - field name to be used in TermQuery. Default is 'ab'\n" +
      "\t\t[<term>] - term used to verify the number of hits. Default is 'dengue'")
    System.exit(0)
  }

  val size = args.length
  if (size < 1) usage()

  val field = if (size > 1) args(1) else "ab"
  val term = if (size > 2) args(2) else "dengue"

  val hits = Try[Int] {
    val directory: FSDirectory = FSDirectory.open(new File(args(0)).toPath)
    val ireader: DirectoryReader = DirectoryReader.open(directory)
    val hitNum: Int = checkDocs(new Term(field,term), ireader)

    ireader.close()
    directory.close()

    hitNum
  } match {
    case Success(h) => h
    case Failure(ex) =>
      println(s"ERROR in IndexText: ${ex.getMessage}")
      0
  }
  println(s"Checking index=${args(0)} field=${args(1)} term=${args(2)} hits=$hits")

  val retValue = if (hits > 255) 255 else hits

  System.exit(retValue)  // Value exit values 8 bits

  private def checkDocs(term: Term,
                        ireader: IndexReader): Int = {
    require(term != null)
    require(ireader != null)

    val numDocs = ireader.numDocs()
    if (numDocs <= 0) throw new Exception("numDocs <= 0")

    val query = new TermQuery(term)
    val isearcher = new IndexSearcher(ireader)
    val topDocs: TopDocs = isearcher.search(query, 1000)
    val totalHits: Long = topDocs.totalHits.value

    if (totalHits <= 0) throw new Exception("totalHits <= 0")

    topDocs.scoreDocs.foreach {
      scoreDoc =>
        val doc = ireader.document(scoreDoc.doc)
        doc.getFields().asScala.map(_.stringValue())
    }

    totalHits.toInt
  }
}