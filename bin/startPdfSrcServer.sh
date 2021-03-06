#!/usr/bin/env bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer-dev/ || exit

export SBT_OPTS="-Xms1024m -Xmx4g -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m /usr/local/sbt/bin/sbt "runMain org.bireme.xds.XDocServer.HttpPdfSrcServer -pdfDir=pdfs -solrColUrl=http://localhost:9293/solr/pdfs -serverPort=9191"

cd - || exit
