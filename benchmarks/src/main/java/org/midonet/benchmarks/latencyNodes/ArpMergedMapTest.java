package org.midonet.benchmarks.latencyNodes;

import mpi.MPI;
import mpi.MPIException;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.ARP;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.observers.TestObserver;
import scala.Tuple2;

import java.util.*;

/**
 * Created by huub on 24-8-15.
 */
public class ArpMergedMapTest implements TestReader, TestWriter {

    private static final Logger log =
            LoggerFactory.getLogger(ArpMergedMapTest.class);

    private MergedMap<IPv4Addr, ArpCacheEntry> map;
    protected IPv4Addr[] randomIpSet;
    private TestObserver obs;
    private int readOffset = 0;
    private Random random;

    public static IPv4Addr[] generateSetOfRandomIps(int size) {
        IPv4Addr[] list = new IPv4Addr[size];

        for (int i=0; i<size; i++) {
            list[i] = IPv4Addr.random();
        }

        return list;
    }

    public static IPv4Addr[] distributeRandomIPSet(int size, int myRank) {
        IPv4Addr[] ips;
        int[] intIps = new int[size];
        try {
            if (myRank == 0) {
                ips = generateSetOfRandomIps(size);
                for (int i = 0; i < size; i++) {
                    intIps[i] = ips[i].toInt();
                }
                MPI.COMM_WORLD.bcast(intIps, size, MPI.INT, 0);
            } else {
                ips = new IPv4Addr[size];
                MPI.COMM_WORLD.bcast(intIps, size, MPI.INT, 0);
                for (int i = 0; i < size; i++) {
                    ips[i] = IPv4Addr.fromInt(intIps[i]);
                }

            }

            log.debug("Ip set distributed among nodes: [" + ips[0].toString() + ",...," + ips[ips.length-1].toString() + "]");

            return ips;
        } catch (MPIException e) {
            log.error("Distribution of IPs failed due to MPIException", e);
            return null;
        }
    }

    public ArpMergedMapTest(MergedMap<IPv4Addr, ArpCacheEntry> mapUnderTest, IPv4Addr[] ipSet, Random theOracle) {
        this.randomIpSet = ipSet;
        this.random = theOracle;
        this.map = mapUnderTest;
        this.obs = ArpMergedMap.arpMapObserver(mapUnderTest);
    }

    private long getCurrentTime() {
        try {
            return Math.round(MPI.wtime() * 1000000.0);
        } catch (MPIException e) {
            log.error("MPI Time exception", e);
            e.printStackTrace();
        }
        return 0;
    }

    public long readEntry() {
        this.readOffset++;
        ArpMergedMap.awaitForObserverEvents(obs, this.readOffset, 5000);
        Tuple2 notif = (Tuple2<IPv4Addr, ArpCacheEntry>) obs.getOnNextEvents().get(this.readOffset - 1);
        ArpCacheEntry entry = (ArpCacheEntry) notif._2();

        return (this.getCurrentTime() - entry.stale);
    }

    public void writeEntry() {
        IPv4Addr ip = randomIpSet[random.nextInt(randomIpSet.length)];
        MAC mac = MAC.random();
        ArpCacheEntry entry =  new ArpCacheEntry(mac, 0 /*expiry*/, this.getCurrentTime() /*stale*/, 0 /*lastArp*/);
        map.putOpinion(ip, entry);
    }


}
