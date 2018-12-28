#!/usr/bin/env bash

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=${JAVA_HOME}
J2REDIR=${JAVA_HOME}/jre
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer/

export SBT_OPTS="-Xms1024m -Xmx1024m -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpUpdDocServer -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:8983/solr/pdfs"

cd -
