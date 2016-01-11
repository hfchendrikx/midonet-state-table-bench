rm -r /usr/share/jmxtrans/jmxtrans-251/config
cp -r /proj/midonet/lattest/jmx/config /usr/share/jmxtrans/jmxtrans-251/config
cd /usr/share/jmxtrans/jmxtrans-251
java -jar lib/jmxtrans-all.jar -s 10 -j config
