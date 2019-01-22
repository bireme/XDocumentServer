#!/usr/bin/env bash

# Vai para diretório da aplicação XDocumentServer
cd /home/javaapps/sbt-projects/XDocumentServer/

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=${JAVA_HOME}
J2REDIR=${JAVA_HOME}/jre
PATH=${JAVA_HOME}/bin:${PATH}

# Servidor
SERVER=basalto01.bireme.br

# Diretório da Solr
SOLR_DIR=/usr/local/solr-7.5.0

# Porta do servidor Solr
SOLR_PORT=9292

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
${SOLR_DIR}/bin/solr start -p ${SOLR_PORT}

cd -
