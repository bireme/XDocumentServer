#!/bin/sh

XDOCSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer
cd $XDOCSERVER_DIR

sbt "runMain org.bireme.xds.XDocServer.IndexTest $1 $2 $3" > test.txt
hits="$?"

cd -

exit $hits
