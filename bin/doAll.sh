#!/bin/bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

XDOCUMENTSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer-dev

cd $XDOCUMENTSERVER_DIR/bin || exit

./delstart.sh

./updateDocuments.sh -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://thumbnailserver.bvsalud.org/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --reset

./startPdfSrcServer.sh

./startThumbServer.sh

cd - || exit
