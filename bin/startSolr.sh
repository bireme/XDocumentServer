#!/usr/bin/env bash

XDOCUMENTSERVER_PATH=/home/javaapps/sbt-projects/XDocumentServer

cd $XDOCUMENTSERVER_PATH || exit

SOLR_PATH=$XDOCUMENTSERVER_PATH/solr-8.11.2
SOLR_PORT=9293

$SOLR_PATH/bin/solr start -m 2g -p $SOLR_PORT

cd - || exit
