#!/bin/bash

sudo sfdisk /dev/sdb < /proj/midonet/lattest/ssd.layout
sudo mkfs.ext4 /dev/sdb1
sudo mkdir /solidstatedrive
sudo mount /dev/sdb1 /solidstatedrive
sudo mkdir /solidstatedrive/kafka
sudo mkdir /solidstatedrive/zookeeper
sudo chmod 777 /solidstatedrive
sudo chmod 777 /solidstatedrive/kafka
sudo chmod 777 /solidstatedrive/zookeeper
