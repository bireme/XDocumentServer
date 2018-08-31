#!/usr/bin/env bash

cd /home/javaapps/sbt-projects/XDocumentServer/

screen -d -m sbt "runMain org.bireme.xds.XDocServer.HttpThumbnailServer -thumbDir=thumbnails -pdfDir=pdfs"

cd -