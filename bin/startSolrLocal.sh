#!/usr/bin/env bash

XDOCUMENTSERER_PATH=/home/javaapps/sbt-projects/XDocumentServer

cd $XDOCUMENTSERER_PATH || exit

SOLR_PATH=$XDOCUMENTSERER_PATH/solr-8.11.2
SOLR_PORT=9293

$SOLR_PATH/bin/solr start -m 2g -p $SOLR_PORT

cd - || exit
