#!/bin/sh

JAVA_HOME=/usr/local/java11
PATH=$JAVA_HOME/bin:$PATH

XDOCSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer-dev
cd $XDOCSERVER_DIR

sbt "runMain org.bireme.xds.XDocServer.IndexTest $1 $2 $3"
hits="$?"

cd -

exit $hits
