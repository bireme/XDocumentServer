#!/bin/bash

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=${JAVA_HOME}
J2REDIR=${JAVA_HOME}/jre
PATH=${JAVA_HOME}/bin:${PATH}

XDOCUMENTSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer

cd $XDOCUMENTSERVER_DIR/bin

./delstart.sh

./updateDocuments.sh -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://thumbnailserver.bvsalud.org/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --reset

./startPdfSrcServer.sh

./startThumbServer.sh

cd - 
