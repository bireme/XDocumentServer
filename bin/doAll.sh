#!/bin/bash

XDOCUMENTSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer

cd $XDOCUMENTSERVER_DIR/bin || exit

./delstart.sh

./updateDocuments.sh -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://thumbnailserver.bvsalud.org/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --reset

./startPdfSrcServer.sh

./startThumbServer.sh

cd - || exit
