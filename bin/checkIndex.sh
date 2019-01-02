#!/bin/sh

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

XDOCSERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer
cd $XDOCSERVER_DIR

sbt "runMain org.bireme.xds.XDocServer.IndexTest $1 $2 $3"
hits="$?"

cd -

exit $hits
