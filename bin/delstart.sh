#!/usr/bin/env bash

cd /home/javaapps/sbt-projects/XDocumentServer

SOLR_PATH=/usr/local/solr-7.5.0
SOLR_PORT=9292

$SOLR_PATH/bin/solr stop -p $SOLR_PORT
$SOLR_PATH/bin/solr start -p $SOLR_PORT

$SOLR_PATH/bin/solr delete -c pdfs

$SOLR_PATH/bin/solr create -c pdfs -d pdf-solr-conf

cd -
