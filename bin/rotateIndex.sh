#!/usr/bin/env bash

XDOCUMENTSERVER_PATH=/home/javaapps/sbt-projects/XDocumentServer

# Vai para diretório da aplicação XDocumentServer
cd $XDOCUMENTSERVER_PATH || exit

# Diretório da Solr
SOLR_DIR=$XDOCUMENTSERVER_PATH/solr-8.11.2

# Porta do servidor Solr
SOLR_PORT=9293

# Diretório das coleções
COL_DIR=$SOLR_DIR/server/solr

# Diretório no servidor de produçao
SERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer

# Faz a rotação do índice 'pdfs'
${SOLR_DIR}/bin/solr stop -p ${SOLR_PORT}
sleep 10s
if [ -d "${COL_DIR}/pdfs" ]; then rm -r ${COL_DIR}/pdfs; fi
#mv ${COL_DIR}/pdfs.new ${COL_DIR}/pdfs
mv ${SERVER_DIR}/tmp/index/pdfs ${COL_DIR}

${SOLR_DIR}/bin/solr start -m 2g -p ${SOLR_PORT}
result="$?"

exit $result
