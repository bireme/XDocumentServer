#!/usr/bin/env bash

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

cd /home/javaapps/sbt-projects/XDocumentServer-dev/ || exit

export SBT_OPTS="-Xms1024m -Xmx1024m -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m"

/usr/local/sbt/bin/sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments $1 $2 $3 $4 $5 $6"
ret="$?"

cd - || exit

exit $ret
