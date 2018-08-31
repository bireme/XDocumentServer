#!/usr/bin/env bash

cd /home/javaapps/sbt-projects/XDocumentServer/

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpPdfSrcServer -pdfDir=pdfs -solrColUrl=http://localhost:8983/solr/pdfs -serverPort=9191"

cd -