#!/bin/sh

XD_DIR=/home/javaapps/sbt-projects/XDocumentServer
cd $XD_DIR || exit

../jetty-home-11.0.14/bin/jetty.sh start

sleep 120s

cd -

exit $ret
