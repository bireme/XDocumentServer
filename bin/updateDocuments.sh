#!/usr/bin/env bash

cd /home/javaapps/sbt-projects/XDocumentServer/

sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments $1 $2 $3 $4 $5 $6 $7"

cd -