#!/bin/bash

NOW=$(date +"%Y%m%d%H%M%S")

XDOCUMENTSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer

# Vai para diretório da aplicação XDocumentServer
cd $XDOCUMENTSERVER_DIR || exit

sh -x bin/XDocumentServer_core.sh &> logs/XDocumentServer_$NOW.log

cd - || exit
