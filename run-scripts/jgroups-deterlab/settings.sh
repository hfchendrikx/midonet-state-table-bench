#!/bin/bash

####################################################################################
# This file contains the settings used for the benchmark setup scripts on deterlab #
####################################################################################

PROJECT_DIR=/proj/midonet
SETUP_ROOT_DIR=$PROJECT_DIR/exp/EXPERIMENT_NAME/setup

#Broker install source directory
BROKER_INSTALL_SRC=$SETUP_ROOT_DIR/broker
CLUSTER_JAR_NAME=midonet-cluster-5.1-SNAPSHOT-all.jar

#Location of slurm configuration file
SLURM_CONFIG_LOCATION=$SETUP_ROOT_DIR/slurm.conf

#Java 8 package
JAVA_8_UBUNTU_PKG=$PROJECT_DIR/rpms/openjdk-8-jre-headless_8u45-b14-1_amd64.deb

#Midonet tools package
MIDONET_TOOLS_PKG=$PROJECT_DIR/rpms/midonet-tools_5.1~201512041431.9d40059_all.deb

#Broker log configurations
BROKER_LOG4J2_CONFIG=$SETUP_ROOT_DIR/log4j2.xml
BROKER_LOGBACK_CONFIG=$SETUP_ROOT_DIR/logback.xml
BROKER_STDOUT_LOCATION=$SETUP_ROOT_DIR/broker-stdout

UTIL_DIR=$SETUP_ROOT_DIR/util