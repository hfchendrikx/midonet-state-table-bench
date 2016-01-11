#!/bin/bash

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/settings.sh

echo -e "\n"
echo -e "######################################\n"
echo -e "# installing java                    #\n"
echo -e "######################################\n"
echo -e "\n"

sudo dpkg -i --ignore-depends=java8-runtime-headless,java8-runtime --force-overwrite --force-confnew $JAVA_8_UBUNTU_PKG
#Install missing dependencies
sudo apt-get -fmVy -o Dpkg::Options::="--force-confnew" install

echo -e "\n"
echo -e "######################################\n"
echo -e "# java -version                      #\n"
echo -e "######################################\n"
echo -e "\n"

java -version

echo -e "######################################\n"
