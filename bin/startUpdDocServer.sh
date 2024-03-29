#!/usr/bin/env bash

XDOCUMENTSERVER_PATH=/home/javaapps/sbt-projects/XDocumentServer

cd $XDOCUMENTSERVER_PATH || exit

export SBT_OPTS="-Xms1024m -Xmx1024m -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpUpdDocServer -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9293/solr/pdfs"

cd - || exit
