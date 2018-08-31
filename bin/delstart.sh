#!/usr/bin/env bash

SOLR_PATH=/usr/local/solr-7.4.0

$SOLR_PATH/bin/solr start

$SOLR_PATH/bin/solr delete -c pdfs

$SOLR_PATH/bin/solr create -c pdfs -d pdf-solr-conf
