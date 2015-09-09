#!/bin/bash
#$1 is use oracle jvm or not

echo -e "\n"
echo -e "######################################\n"
echo -e "# installing java                    #\n"
echo -e "######################################\n"
echo -e "\n"

#always install this one
sudo apt-get -y install openjdk-7-jre

#/opt/java/jdk1.7.0_79 is created by the deterlab ns file
if [ $1 -eq 1 ]; then
   echo "Setting oracle jvm as alternative\n";
   sudo update-alternatives --install /usr/bin/java java /opt/java/jdk1.7.0_79/bin/java 1500
   sudo update-alternatives --install /usr/bin/javac javac /opt/java/jdk1.7.0_79/bin/javac 1500
fi

echo -e "\n"
echo -e "######################################\n"
echo -e "# java -version                      #\n"
echo -e "######################################\n"
echo -e "\n"

java -version

echo -e "######################################\n"
