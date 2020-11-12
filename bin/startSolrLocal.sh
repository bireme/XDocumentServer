#!/usr/bin/env bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer-dev || exit

SOLR_PATH=/usr/local/solr-8.5.2
SOLR_PORT=9293

$SOLR_PATH/bin/solr start -m 2g -p $SOLR_PORT

cd - || exit
