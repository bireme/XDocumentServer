#!/usr/bin/env bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer-dev/ || exit

export SBT_OPTS="-Xms1024m -Xmx1024m -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m /usr/local/sbt/bin/sbt "runMain org.bireme.xds.XDocServer.HttpUpdDocServer -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9293/solr/pdfs"

cd - || exit
