#!/usr/bin/env bash

# Vai para diretório da aplicação XDocumentServer
cd /home/javaapps/sbt-projects/XDocumentServer/

# Se 1 apaga índice anterior e indexa todos os documentos pdfs, caso contrário, indexa os documentos pdfs a partir de certa data
FULL_INDEXING=0

# A partir de quantos dias se faz a atualizacao
DAYS_AGO=1

# Data de inicio da atualização
FROM_DAY= `date --date="$DAYS_AGO days ago" +%Y-%m-%d`

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

# Diretório da Solr
SOLR_DIR=/usr/local/solr-7.5.0

# Porta do servidor Solr
SOLR_PORT=9292

# Diretório das coleções
COL_DIR=$SOLR_DIR/server/solr/

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
  cp -r $COL_DIR/pdfs old/index
fi

# Move 'pdfs' para diretório 'old'
if [ -e "pdfs" ]; then
  mv pdfs old
fi

# Move 'thumbnails' para diretório 'old'
if [ -e "thumbnails" ]; then
  mv thumbnails old
fi

# Gera os arquivos pdfs e thumbnails e o índice lucene
if [ "$FULL_INDEXING" -eq 0 ]; then
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://thumbnailserver.bvsalud.org/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --onlyMissing"
else
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://thumbnailserver.bvsalud.org/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --reset"
fi
ret="$?"

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$?" -ne 0 ]; then
  if [ -e "bug" ]; then
    rm -r bug
  fi
  mkdir bug
  mv pdfs bug
  mv thumbnails bug
  mk index
  mv $COL_DIR/pdfs bug/index
  mv old/pdfs pd$COL_DIR/pdfsfs_old
  mv old/thumbnails thumbnailserver
  mv old/index/pdfs $COL_DIR
  sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents ERROR - `date '+%Y-%m-%d'`" -m "XDocumentServer - Erro na geracao de pdfs e/ou thumbnails" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa índice 'pdfs' recém-criado
bin/checkIndex.sh index/pdfs ab salud
hitsLocal="$?"

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$hitsLocal" -ne 0 ]; then
  if [ -e "bug" ]; then
    rm -r bug
  fi
  mkdir bug
  mv pdfs bug
  mv thumbnails bug
  mk index
  mv $COL_DIR/pdfs bug/index
  mv old/pdfs pdfs_old
  mv old/thumbnails thumbnailserver
  mv old/index/pdfs $COL_DIR
  sendemail -f appofi@bireme.org -u "XDocumentServer - Local index check ERROR - `date '+%Y-%m-%d'`" -m "XDocumentServer - Erro na checagem de índice local 'pdfs'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia diretório 'pdfs' para servidor de produção
$MISC/sendFiles.sh pdfs $SERVER:$SERVER_DIR/
result="$?"
if [ "$result" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'pdfs' para $SERVER:$SERVER_DIR/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia diretório 'thumbnails' para servidor de produção
$MISC/sendFiles.sh thumbanails $SERVER:$SERVER_DIR/
result="$?"
if [ "$result" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'thumbnails' para $SERVER:$SERVER_DIR/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia índice 'pdfs' para servidor de produção
$MISC/sendFiles.sh $COL_DIR/pdfs $SERVER:$SERVER_DIR/index
result="$?"
if [ "$result" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Index 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do índice 'pdfs' para $SERVER:$SERVER_DIR/index/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1SOLR_PATH=/usr/local/solr-7.5.0
fi

# Checa qualidade dos índice 'pdfs'
ssh $TRANSFER@$SERVER $SERVER_DIR/bin/checkIndex.sh index/pdfs.new ab salud
hitsRemoto="$?"

if [ "$hitsRemoto" -ne "$hitsLocal" ]; then  # Índice apresenta problemas
  sendemail -f appofi@bireme.org -u "XDocumentServer - Remote index check ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na checagem da qualidade do índice remoto 'pdfs' no servidor de produção" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Faz a rotação do diretório 'pdfs'
ssh $TRANSFER@$SERVER "if [ -e '$SERVER_DIR/pdfs' ]; then rm -r $SERVER_DIR/pdfs; fi"
ssh $TRANSFER@$SERVER "mv $SERVER_DIR/pdfs.new $SERVER_DIR/pdfs"

# Faz a rotação do diretório 'thumbnails'
ssh $TRANSFER@$SERVER "if [ -e '$SERVER_DIR/thumbnails' ]; then rm -r $SERVER_DIR/thumbnails; fi"
ssh $TRANSFER@$SERVER "mv $SERVER_DIR/thumbnails.new $SERVER_DIR/thumbnails"

# Faz a rotação do índice 'pdfs'
ssh $TRANSFER@$SERVER "$SOLR_DIR/bin/solr stop -p $SOLR_PORT"
sleep 10s
ssh $TRANSFER@$SERVER "if [ -e '$COL_DIR/pdfs' ]; then rm -r $COL_DIR/pdfs; fi"
ssh $TRANSFER@$SERVER "mv $SERVER_DIR/pdfs.new $COL_DIR/pdfs"
ssh $TRANSFER@$SERVER "$SOLR_DIR/bin/solr start -p $SOLR_PORT"

cd -

# Manda email avisando que a geração ocorreu corretamente
sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents finished - `date '+%Y-%m-%d'`" -m "XDocumentServer - Processo de geracao de pdfs e/ou thumbnails finalizou corretamente" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br

exit 0
