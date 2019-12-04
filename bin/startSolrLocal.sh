#!/usr/bin/env bash

#JAVA_HOME=/usr/local/oracle-8-jdk
#J2SDKDIR=${JAVA_HOME}
#J2REDIR=${JAVA_HOME}/jre
#PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer

SOLR_PATH=/usr/local/solr-7.5.0
SOLR_PORT=9292

$SOLR_PATH/bin/solr start -m 2g -p $SOLR_PORT

cd -
