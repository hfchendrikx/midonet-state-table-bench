echo "Sleeping 1m before starting log"
cd /proj/midonet/lattest/jmx
rm /usr/share/jmxtrans/jmxtrans-251/logs/*
sleep 1m
./start-jmx.sh
