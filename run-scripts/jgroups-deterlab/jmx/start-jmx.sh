#!/bin/bash

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/../settings.sh

rm -r /usr/share/jmxtrans/jmxtrans-251/config
cp -r $SETUP_ROOT_DIR/jmx/config /usr/share/jmxtrans/jmxtrans-251/config
cd /usr/share/jmxtrans/jmxtrans-251
java -jar lib/jmxtrans-all.jar -s 10 -j config
