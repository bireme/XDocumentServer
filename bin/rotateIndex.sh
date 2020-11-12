#!/usr/bin/env bash

# Vai para diretório da aplicação XDocumentServer
cd /home/javaapps/sbt-projects/XDocumentServer-dev/ || exit

JAVA_HOME=/usr/local/java11
PATH=${JAVA_HOME}/bin:${PATH}

# Diretório da Solr
SOLR_DIR=/usr/local/solr-8.5.2

# Porta do servidor Solr
SOLR_PORT=9293

# Diretório das coleções
COL_DIR=$SOLR_DIR/server/solr

# Diretório no servidor de produçao
SERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer-dev

# Faz a rotação do índice 'pdfs'
${SOLR_DIR}/bin/solr stop -p ${SOLR_PORT}
sleep 10s
if [ -d "${COL_DIR}/pdfs" ]; then rm -r ${COL_DIR}/pdfs; fi
#mv ${COL_DIR}/pdfs.new ${COL_DIR}/pdfs
mv ${SERVER_DIR}/tmp/index/pdfs ${COL_DIR}

${SOLR_DIR}/bin/solr start -m 2g -p ${SOLR_PORT}
result="$?"

exit $result
