#!/bin/bash

echo "Starting slurm..."
sudo adduser --disabled-password --gecos "" munge || true
sudo adduser --disabled-password --gecos "" slurm || true
sudo chown munge:munge /home/munge
sudo chown slurm:slurm /home/slurm
sudo chown munge:munge /var/lib/munge
sudo chown munge:munge /etc/munge/munge.key
sudo chown munge:munge /etc/munge
sudo chown munge:munge /var/run/munge
sudo chown munge:munge /var/log/munge
sudo chown munge:munge /var/log/munge/munged.log
sudo rm /etc/slurm-llnl/slurm.conf
sudo cp /proj/midonet/slurm/slurm.conf /etc/slurm-llnl/slurm.conf
sudo /etc/init.d/munge start
sudo /etc/init.d/slurm-llnl start
