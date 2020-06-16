#!/usr/bin/env bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer || exit

SOLR_PATH=/usr/local/solr-7.5.0
SOLR_PORT=9292

$SOLR_PATH/bin/solr stop -p $SOLR_PORT
$SOLR_PATH/bin/solr start -m 2g -p $SOLR_PORT

$SOLR_PATH/bin/solr delete -c pdfs

$SOLR_PATH/bin/solr create -c pdfs -d pdf-solr-conf

cd - || exit
