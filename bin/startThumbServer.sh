#!/usr/bin/env bash

cd /home/javaapps/sbt-projects/XDocumentServer/

export SBT_OPTS="-Xms1024m -Xmx4g -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpThumbnailServer -thumbDir=thumbnails -pdfDir=pdfs"

cd -