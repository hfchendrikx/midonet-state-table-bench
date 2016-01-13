#!/bin/bash

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/settings.sh

nohup $SETUP_ROOT_DIR/jmx/start-jmx.sh </dev/null > /dev/null 2>&1 &
