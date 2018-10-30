/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

class DocumentServerImpl(docServer: DocumentServer) extends DocumentServer {
  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  override def getDocuments: Set[String] = docServer.getDocuments

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @return the original document content (bytes) if it is found/created or 404(not found) or 500 (internal server error)
    */
  override def getDocument(id: String): Either[Int, InputStream] = docServer.getDocument(id)

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document content
    * @param info metainfo of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              source: InputStream,
                              info: Option[Map[String, Seq[String]]] = None): Int =
    docServer.createDocument(id, source, info)

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              url: String,
                              info: Option[Map[String, Seq[String]]]): Int =
    docServer.createDocument(id, url, info)

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source the source of the document content
    * @param info metainfo of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               source: InputStream,
                               info: Option[Map[String, Seq[String]]] = None): Int =
    docServer.replaceDocument(id, source, info)

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  def replaceDocument(id: String,
                      url: String,
                      info: Option[Map[String, Seq[String]]]): Int =
    docServer.replaceDocument(id, url, info)

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  override def deleteDocument(id: String): Int = docServer.deleteDocument(id)

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  override def getDocumentInfo(id: String): Either[Int, Map[String, Seq[String]]] = docServer.getDocumentInfo(id)

  /**
    * Create a metadata for the document
    * @param id document identifier
    * @param source source of the document content
    * @param info other metadata source
    * @return the document metadata
    */
  override def createDocumentInfo(id: String,
                                  source: Option[InputStream]= None,
                                  info: Option[Map[String, Seq[String]]] = None): Map[String, Seq[String]] = {
    val now: Date = Calendar.getInstance().getTime
    val dateFormat: SimpleDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
    val date: String = dateFormat.format(now)

    Map("id" -> Seq(id), "date" -> Seq(date)) ++ info.getOrElse(Map[String, Seq[String]]())
  }
}
