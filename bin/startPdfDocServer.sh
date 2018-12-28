#!/usr/bin/env bash

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=${JAVA_HOME}
J2REDIR=${JAVA_HOME}/jre
PATH=${JAVA_HOME}/bin:${PATH}


cd /home/javaapps/sbt-projects/XDocumentServer/

export SBT_OPTS="-Xms1024m -Xmx4g -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpPdfDocServer -pdfDir=pdfs"

cd -
