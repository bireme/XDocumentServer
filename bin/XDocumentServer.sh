#!/usr/bin/env bash

# Vai para diretório da aplicação XDocumentServer
cd /home/javaapps/sbt-projects/XDocumentServer/

# Se 1 apaga índice anterior e indexa todos os documentos pdfs, caso contrário, indexa somente os documentos pdfs não armazenados
FULL_INDEXING=0

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

# Apaga o diretório de backup 'old'
if [ -e "old" ]; then
 rm -r old
fi

# Cria diretório de backup 'old'
mkdir old

# Copia índice lucene anterior para diretório 'old'
if [ -e "$COL_DIR/pdfs" ]; then
  mkdir -p old/index
  cp -r ${COL_DIR}/pdfs old/index
fi

if [ "$FULL_INDEXING" -eq 1 ]; then
  # Move 'pdfs' para diretório 'old'
  if [ -e "pdfs" ]; then
    mv pdfs old
  fi

  # Move 'thumbnails' para diretório 'old'
  if [ -e "thumbnails" ]; then
    mv thumbnails old
  fi
fi

# Gera os arquivos pdfs e thumbnails e o índice lucene
if [ "$FULL_INDEXING" -eq 0 ]; then
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9292/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --onlyMissing"
else
  bin/delstart.sh  # Reinicializa o índice pdfs e o servidor (que pode ficar com o índice em memória)
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9292/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument"
fi
ret="$?"

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$ret" -ne 0 ]; then
  mv old/pdfs .
  mv old/thumbnails .
  mv old/index/pdfs ${COL_DIR}
  sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents ERROR - `date '+%Y-%m-%d'`" -m "XDocumentServer - Erro na geracao de pdfs e/ou thumbnails" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa índice 'pdfs'
bin/checkIndex.sh $COL_DIR/pdfs/data/index ab salud
hitsLocal="$?"

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$hitsLocal" -eq 0 ]; then
  mv old/pdfs .
  mv old/thumbnails .
  mv old/index/pdfs ${COL_DIR}
  sendemail -f appofi@bireme.org -u "XDocumentServer - Local index check ERROR - `date '+%Y-%m-%d'`" -m "XDocumentServer - Erro na checagem de índice local 'pdfs'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Apaga versão anterior do arquivo "tmp/pdfs.tgz"
if [ -e "tmp/pdfs.tgz" ]; then
  rm tmp/pdfs.tgz
fi

# Apaga versão anterior do arquivo "tmp/thumbnails.tgz"
if [ -e "tmp/thumbnails.tgz" ]; then
  rm tmp/thumbnails.tgz
fi

# Apaga versão anterior do arquivo "tmp/pdfsIndex.tgz"
if [ -e "tmp/index/pdfsIndex.tgz" ]; then
  rm tmp/index/pdfsIndex.tgz
fi

# Compacta diretório pdfs
tar -cvzpf pdfs.tgz pdfs
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'pdfs'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Compacta diretório pdfs
tar -cvzpf thumbnails.tgz thumbnails
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'thumbnails'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Compacta diretório com o índice pdfs
cd $COL_DIR
tar -cvzpf pdfsIndex.tgz pdfs
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfsIndex' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'pdfsIndex'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi
cd -
mv $COL_DIR/pdfsIndex.tgz .

# Cria diretório temporário de transferência no servidor de produção
ssh ${TRANSFER}@${SERVER} mkdir ${SERVER_DIR}/tmp
ssh ${TRANSFER}@${SERVER} mkdir ${SERVER_DIR}/tmp/index

# Copia arquivo 'pdfs.tgz' para servidor de produção
scp pdfs.tgz ${TRANSFER}@${SERVER}:${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'pdfs' para $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia arquivo 'thumbnails.tgz' para servidor de produção
scp thumbnails.tgz ${TRANSFER}@${SERVER}:${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  c -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'thumbnails' para $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia arquivo 'pdfsIndex.tgz' para servidor de produção
scp pdfsIndex.tgz ${TRANSFER}@${SERVER}:${SERVER_DIR}/tmp/index
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Index 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do índice 'pdfs' para $SERVER:$SERVER_DIR/tmp/index" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'pdfs.tgz' no diretório de produção
ssh ${TRANSFER}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/pdfs.tgz -C ${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'pdfs.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'pdfs.tgz' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'thumbnails.tgz' no diretório de produção
ssh ${TRANSFER}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/thumbnails.tgz -C ${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'thumbnails.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'thumbnails.tgz' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'pdfsIndex.tgz' no diretório de produção
ssh ${TRANSFER}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/index/pdfsIndex.tgz -C ${SERVER_DIR}/tmp/index
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'thumbnails.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'thumbnails.tgz' em $SERVER:$SERVER_DIR/tmp/index" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi


# Copia diretório 'pdfs' para servidor de produção
#${MISC}/sendFiles.sh pdfs ${SERVER}:${SERVER_DIR}/
#result="$?"
#if [ "${result}" -ne 0 ]; then
#  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'pdfs' para $SERVER:$SERVER_DIR/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
#  exit 1
#fi

## Copia diretório 'thumbnails' para servidor de produção
#${MISC}/sendFiles.sh thumbnails $SERVER:$SERVER_DIR/
#result="$?"
#if [ "${result}" -ne 0 ]; then
#  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'thumbnails' para $SERVER:$SERVER_DIR/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
#  exit 1
#fi

# Copia índice 'pdfs' para servidor de produção
#${MISC}/sendFiles.sh $COL_DIR/pdfs $SERVER:$COL_DIR/
#result="$?"
#if [ "${result}" -ne 0 ]; then
#  sendemail -f appofi@bireme.org -u "XDocumentServer - Index 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do índice 'pdfs' para $SERVER:$SERVER_DIR/index/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
#  exit 1
#fi

# Checa qualidade dos índice 'pdfs'
#ssh ${TRANSFER}@${SERVER} ${SERVER_DIR}/bin/checkIndex.sh $COL_DIR/pdfs.new/data/index ab salud
#hitsRemoto="$?"

#if [ "${hitsRemoto}" -ne "${hitsLocal}" ]; then  # Índice apresenta problemas
#  sendemail -f appofi@bireme.org -u "XDocumentServer - Remote index check ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na checagem da qualidade do índice remoto 'pdfs' no servidor de produção" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
#  exit 1
#fi

# Checa qualidade dos índice 'pdfs'
ssh ${TRANSFER}@${SERVER} ${SERVER_DIR}/bin/checkIndex.sh ${SERVER_DIR}/tmp/index/pdfs/data/index ab salud
hitsRemoto="$?"

if [ "${hitsRemoto}" -ne "${hitsLocal}" ]; then  # Índice apresenta problemas
  sendemail -f appofi@bireme.org -u "XDocumentServer - Remote index check ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na checagem da qualidade do índice remoto 'pdfs' no servidor de produção" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi


# Faz a rotação do diretório 'pdfs'
ssh ${TRANSFER}@${SERVER} rm -r ${SERVER_DIR}/pdfs
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'pdfs' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'pdfs' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi
ssh ${TRANSFER}@${SERVER} mv ${SERVER_DIR}/tmp/pdfs ${SERVER_DIR}/
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'pdfs' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'pdfs' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Faz a rotação do diretório 'thumbnails'
ssh ${TRANSFER}@${SERVER} rm -r ${SERVER_DIR}/thumbnails
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'thumbnails' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'thumbnails' em $SERVER:$SERVER_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi
ssh ${TRANSFER}@${SERVER} mv ${SERVER_DIR}/tmp/thumbnails ${SERVER_DIR}/
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'thumbnails' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'thumbnails' em $SERVER:$SERVER_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Faz a rotação do índice 'pdfs'
ssh ${TRANSFER}@${SERVER} ${SERVER_DIR}/bin/rotateIndex.sh
result="$?"
if [ "${result}" -eq 255 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating index 'pdfs' ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do índice 'pdfs' em $SERVER:$COL_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

cd -

# Manda email avisando que a geração ocorreu corretamente
sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents finished - `date '+%Y-%m-%d'`" -m "XDocumentServer - Processo de geracao de pdfs e/ou thumbnails finalizou corretamente" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br

exit 0
