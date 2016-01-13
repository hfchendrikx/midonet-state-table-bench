#!/bin/bash

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/../settings.sh

echo "Sleeping 1m before starting log"
cd $SETUP_ROOT_DIR/jmx
rm /usr/share/jmxtrans/jmxtrans-251/logs/*
sleep 1m
./start-jmx.sh
