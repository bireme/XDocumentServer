#!/usr/bin/env bash

NOW=$(date +"%Y%m%d%H%M%S")

XDOCSERVER_HOME=/home/javaapps/sbt-projects/XDocumentServer

# Vai para diretório da aplicação XDocumentServer
cd $XDOCSERVER_HOME || exit

# Se 1 apaga índice anterior e indexa todos os documentos pdfs, caso contrário, indexa somente os documentos pdfs não armazenados
FULL_INDEXING=1

# Servidor
SERVER=diamante15.bireme.br

# Diretório da Solr
SOLR_DIR=$XDOCSERVER_HOME/solr-8.11.2

# Arquivo log
LOG_FILE=$XDOCSERVER_HOME/logs/log_$NOW.txt

# Diretório das coleções
COL_DIR=$SOLR_DIR/server/solr

# Diretório no servidor de produçao
SERVER_DIR=/home/javaapps/sbt-projects/XDocumentServer

# Cria diretório de logs
mkdir -p $XDOCSERVER_HOME/logs

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
  bin/startSolr.sh   # Se o Solr tiver caído, inicia-o
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -decsPath=/bases/dec.000/dec.dec/output/decs -solrColUrl=http://localhost:9293/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument --addMissing --updateChanged" > $LOG_FILE
  ret="$?"
else
  bin/delstart.sh  # Reinicializa o índice pdfs e o servidor (que pode ficar com o índice em memória)
  sbt "runMain org.bireme.xds.XDocServer.UpdateDocuments -pdfDocDir=pdfs -thumbDir=thumbnails -decsPath=/bases/dec.000/dec.dec/output/decs -solrColUrl=http://localhost:9293/solr/pdfs -thumbServUrl=http://thumbnailserver.bvsalud.org/getDocument" > $LOG_FILE
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

# Compacta diretório pdfs
tar -cvzpf pdfs.tgz pdfs
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'pdfs'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Compacta diretório thumbnails
tar -cvzpf thumbnails.tgz thumbnails
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'thumbnails'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Compacta diretório com o índice pdfs
cd $COL_DIR || exit
tar -cvzpf pdfsIndex.tgz pdfs
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfsIndex' compression ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na compactação do diretório 'pdfsIndex'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi
cd - || exit
mv $COL_DIR/pdfsIndex.tgz .

exit 0


