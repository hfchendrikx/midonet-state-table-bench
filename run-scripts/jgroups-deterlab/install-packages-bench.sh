#!/bin/bash
MAIN_DIR=/proj/midonet/lattest
sudo apt-get update
$MAIN_DIR/install-java.sh
sudo apt-get -y install pdsh
