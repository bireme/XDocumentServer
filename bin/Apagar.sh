#!/usr/bin/env bash

XDOCSERVER_HOME=/home/javaapps/sbt-projects/XDocumentServer

# Vai para diretório da aplicação XDocumentServer
cd $XDOCSERVER_HOME

# Se 1 apaga índice anterior e indexa todos os documentos pdfs, caso contrário, indexa somente os documentos pdfs não armazenados
FULL_INDEXING=1

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
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9292/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --addMissing --updateChanged" > log.txt
  ret="$?"
else
  bin/delstart.sh  # Reinicializa o índice pdfs e o servidor (que pode ficar com o índice em memória)
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -solrColUrl=http://localhost:9292/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument" > log.txt
  ret="$?"
fi

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$ret" -ne 0 ]; then
  if [ -e "pdfs" ]; then
    rm -r pdfs
  fi
  if [ -e "old/pdfs" ]; then
    mv old/pdfs .
  fi
  if [ -e "thumbnails" ]; then
    rm -r thumbnails
  fi
  if [ -e "old/thumbnails" ]; then
    mv old/thumbnails .
  fi
  if [ -e "${COL_DIR}/pdfs" ]; then
    rm -r  ${COL_DIR}/pdfs
  fi
  if [ -e "old/index/pdfs" ]; then
    mv old/index/pdfs ${COL_DIR}
  fi
  sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents ERROR - `date '+%Y-%m-%d'`" -m "XDocumentServer - Erro na geracao de pdfs e/ou thumbnails" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa índice 'pdfs'
bin/checkIndex.sh $COL_DIR/pdfs/data/index ab salud
hitsLocal="$?"

# Se ocorreu erro restaura situação anterior, manda email e sai
if [ "$hitsLocal" -eq 0 ]; then
  if [ -e "pdfs" ]; then
    rm -r pdfs
  fi
  if [ -e "old/pdfs" ]; then
    mv old/pdfs .
  fi
  if [ -e "thumbnails" ]; then
    rm -r thumbnails
  fi
  if [ -e "old/thumbnails" ]; then
    mv old/thumbnails .
  fi
  if [ -e "${COL_DIR}/pdfs" ]; then
    rm -r  ${COL_DIR}/pdfs
  fi
  if [ -e "old/index/pdfs" ]; then
    mv old/index/pdfs ${COL_DIR}
  fi
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
