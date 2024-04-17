#!/usr/bin/env bash

export HORA_INICIO=`date '+%s'`
export HI="`date '+%Y.%m.%d %H:%M:%S'`"

echo "[TIME-STAMP] `date '+%Y.%m.%d %H:%M:%S'` [:INI:] Processa ${0} ${1} ${3} ${4} ${5}"
echo ""

WHOAMI=`whoami`
if [ "$WHOAMI" != "operacao" ]
then
    echo "You should execute this shell as 'operacao' user"
    exit 1
fi

NOW=$(date +"%Y%m%d%H%M%S")

XDOCSERVER_HOME=/home/javaapps/sbt-projects/XDocumentServer

# Vai para diretório da aplicação XDocumentServer
cd $XDOCSERVER_HOME || exit

# Se 1 apaga índice anterior e indexa todos os documentos pdfs, caso contrário, indexa somente os documentos pdfs não armazenados
FULL_INDEXING=1

# User
OPERACAO=operacao

# Servidor
SERVER=diamante15.bireme.br

#  Porta no servidor
PORT=8022

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

echo "hitsLocal=$hitsLocal"

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

# Apaga diretório temporário de transferência no servidor de produção
ssh -p ${PORT} ${OPERACAO}@${SERVER} rm -r ${SERVER_DIR}/tmp

# Cria diretório temporário de transferência no servidor de produção
ssh -p ${PORT} ${OPERACAO}@${SERVER} mkdir ${SERVER_DIR}/tmp
ssh -p ${PORT} ${OPERACAO}@${SERVER} mkdir ${SERVER_DIR}/tmp/index

# Copia arquivo 'pdfs.tgz' para servidor de produção
scp -P ${PORT} pdfs.tgz ${OPERACAO}@${SERVER}:${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Directory 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'pdfs' para $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia arquivo 'thumbnails.tgz' para servidor de produção
scp -P ${PORT} thumbnails.tgz ${OPERACAO}@${SERVER}:${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  sendmail -f appofi@bireme.org -u "XDocumentServer - Directory 'thumbnails' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do diretório 'thumbnails' para $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia arquivo 'pdfsIndex.tgz' para servidor de produção
scp -P ${PORT} pdfsIndex.tgz ${OPERACAO}@${SERVER}:${SERVER_DIR}/tmp/index
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Index 'pdfs' transfer ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na transferência do índice 'pdfs' para $SERVER:$SERVER_DIR/tmp/index" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'pdfs.tgz' no diretório de produção
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/pdfs.tgz -C ${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'pdfs.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'pdfs.tgz' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'thumbnails.tgz' no diretório de produção
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/thumbnails.tgz -C ${SERVER_DIR}/tmp
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'thumbnails.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'thumbnails.tgz' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Descompacta arquivo 'pdfsIndex.tgz' no diretório de produção
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -xvzpf ${SERVER_DIR}/tmp/index/pdfsIndex.tgz -C ${SERVER_DIR}/tmp/index
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Uncompressing 'thumbnails.tgz' file ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na descompressão do arquivo 'thumbnails.tgz' em $SERVER:$SERVER_DIR/tmp/index" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa qualidade dos índice 'pdfs'
ssh -p ${PORT} ${OPERACAO}@${SERVER} ${SERVER_DIR}/bin/checkIndex.sh ${SERVER_DIR}/tmp/index/pdfs/data/index ab salud
hitsRemoto="$?"

if [ "${hitsRemoto}" -ne "${hitsLocal}" ]; then  # Índice apresenta problemas
  sendemail -f appofi@bireme.org -u "XDocumentServer - Remote index check ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na checagem da qualidade do índice remoto 'pdfs' no servidor de produção" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi


NOW=$(date +"%Y%m%d-%T")

# Cria se não existir o diretório 'old' para guardar as versões compactadas dos diretórios 'pdfs', 'thumbnails' e 'pdfs index' atuais
ssh -p ${PORT} ${OPERACAO}@${SERVER} mkdir -p ${SERVER_DIR}/old

# Faz a rotação do diretório 'pdfs'
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -cvzpf ${SERVER_DIR}/old/pdfs_$NOW.tgz -C ${SERVER_DIR} pdfs
ssh -p ${PORT} ${OPERACAO}@${SERVER} rm -fr ${SERVER_DIR}/pdfs
ssh -p ${PORT} ${OPERACAO}@${SERVER} mv ${SERVER_DIR}/tmp/pdfs ${SERVER_DIR}/
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'tmp/pdfs -> ~' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'tmp/pdfs -> ~' em $SERVER:$SERVER_DIR/tmp" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Faz a rotação do diretório 'thumbnails'
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -cvzpf ${SERVER_DIR}/old/thumbnails_$NOW.tgz -C ${SERVER_DIR} thumbnails
ssh -p ${PORT} ${OPERACAO}@${SERVER} rm -fr ${SERVER_DIR}/thumbnails
ssh -p ${PORT} ${OPERACAO}@${SERVER} mv ${SERVER_DIR}/tmp/thumbnails ${SERVER_DIR}/
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating 'thumbnails' dir ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do diretório 'thumbnails' em $SERVER:$SERVER_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Faz a compressão do índice 'pdfs'
ssh -p ${PORT} ${OPERACAO}@${SERVER} tar -cvzpf ${SERVER_DIR}/old/pdfs_index_$NOW.tgz -C ${COL_DIR}

# Faz a rotação do índice 'pdfs'
ssh -p ${PORT} ${OPERACAO}@${SERVER} ${SERVER_DIR}/bin/rotateIndex.sh
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Rotating index 'pdfs' ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na rotação do índice 'pdfs' em $SERVER:$COL_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Apaga conteúdo do diretório tmp
ssh -p ${PORT} ${OPERACAO}@${SERVER} rm -r ${SERVER_DIR}/tmp/*
result="$?"
if [ "${result}" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "XDocumentServer - Removing tmp contents ERROR - `date '+%Y%m%d'`" -m "XDocumentServer - Erro na remoção dos conteudos do diretório 'tmp' em $SERVER:$SERVER_DIR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

cd - || exit

# Manda email avisando que a geração ocorreu corretamente
sendemail -f appofi@bireme.org -u "XDocumentServer - Updating documents finished - `date '+%Y-%m-%d'`" -m "XDocumentServer - Processo de geracao de pdfs e/ou thumbnails finalizou corretamente" -a $LOG_FILE -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br

echo
echo "DURACAO DE PROCESSAMENTO"
echo "-------------------------------------------------------------------------"
echo " - Inicio:  ${HI}"
echo " - Termino: `date '+%Y.%m.%d %H:%M:%S'`"
echo
echo " Tempo de execucao: ${DURACAO} [s]"
echo " Ou ${HORAS}h ${MINUTOS}m ${SEGUNDOS}s"
echo

# ------------------------------------------------------------------------- #
echo "[TIME-STAMP] `date '+%Y.%m.%d %H:%M:%S'` [:FIM:] Processa  ${0} ${1} ${3} ${4} ${5}"
# ------------------------------------------------------------------------- #
echo

exit 0


