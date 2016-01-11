package org.midonet.cluster.data.storage

import java.net.InetAddress
import java.net.InetAddress._
import java.util
import java.util.concurrent.TimeUnit

import org.midonet.cluster.data.storage.jgroups.JGroupsClient.{Owner, Payload, Key}
import org.midonet.midolman.state.ArpCacheEntry
import org.midonet.packets.IPv4Addr
import org.midonet.util.reactivex.AwaitableObserver
import org.midonet.cluster.data.storage.jgroups.{JGroupsZkClient, JGroupsClient, JGroupsMessageSerializer, JGroupsBus}
import rx.Observer
import rx.observers.TestObserver

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object ArpMergedMap {
    type ArpObs = TestObserver[MergedMap.MapUpdate[IPv4Addr, ArpCacheEntry]]
        with AwaitableObserver[MergedMap.MapUpdate[IPv4Addr, ArpCacheEntry]]

    lazy val jgroupsZkClient = new JGroupsZkClient()
    lazy val availableBrokers:List[String] = jgroupsZkClient.readBrokerConnectionStringsFromZK

    //For benchmarking new values always win, x is the new value
    def arpOrdering() = new Ordering[ArpCacheEntry] {
        override def compare(x: ArpCacheEntry, y: ArpCacheEntry): Int = {
            //(x.stale - y.stale).asInstanceOf[Int]
            1
        }
    }

    def newArpMap(mapId: String, owner: String, initialBroker: Option[Int] = None)
    : MergedMap[IPv4Addr, ArpCacheEntry] = newArpMapAndBus(mapId, owner, initialBroker)._1

    def newArpMapAndBus(mapId: String, owner: String, initialBroker: Int)
    : (MergedMap[IPv4Addr, ArpCacheEntry], MergedMapBus[IPv4Addr, ArpCacheEntry]) =
        newArpMapAndBus(mapId, owner, Some(initialBroker))

    def newArpMapAndBus(mapId: String, owner: String, initialBroker: Option[Int] = None)
    : (MergedMap[IPv4Addr, ArpCacheEntry], MergedMapBus[IPv4Addr, ArpCacheEntry]) = {

        //Quick fix to run testbench locally and on cluster
        val hostname = InetAddress.getLocalHost().getHostName()
        val localname = if (hostname.contains(".")) {
            val parts = hostname.split('.')
            parts(0)
        } else {
            "localhost"
        }

        val client: JGroupsClient = if (initialBroker.isEmpty) {
            new JGroupsClient(localname)
        } else {
            val index = initialBroker.get

            if (availableBrokers.size < index) {
                throw new Exception("Broker index higher than amount of brokers available")
            }

            val initialBrokerAddress = availableBrokers(index)
            new JGroupsClient(localname, initialBrokerAddress)
        }
        val jgroupsBus: JGroupsBus[IPv4Addr, ArpCacheEntry] = new
                JGroupsBus[IPv4Addr, ArpCacheEntry](mapId, owner, ArpEntrySerializer, client)
        (new MergedMap[IPv4Addr, ArpCacheEntry](jgroupsBus)(
            ArpMergedMap.arpOrdering), jgroupsBus)
    }

    def timeoutDuration(timeout: Long): FiniteDuration =
        duration.pairLongToDuration(timeout, TimeUnit.MILLISECONDS)

    def awaitForObserverEvents(obs: ArpObs, eventCount: Int, timeout: Long)
    : Boolean = {
        obs.awaitOnNext(eventCount, timeoutDuration(timeout))
    }

    def arpMapObserver(map: MergedMap[IPv4Addr, ArpCacheEntry])
    : ArpObs = {
        val obs = new TestObserver[map.MapUpdate]()
            with AwaitableObserver[map.MapUpdate]
        map.observable.subscribe(obs)
        obs
    }

    def unsubscribedAwaitableMapObserver() : ArpObs = {
        new TestObserver[MergedMap.MapUpdate[IPv4Addr, ArpCacheEntry]]()
          with AwaitableObserver[MergedMap.MapUpdate[IPv4Addr, ArpCacheEntry]]
    }
}

object ArpEntrySerializer extends JGroupsMessageSerializer[IPv4Addr, ArpCacheEntry] {
//    override def decodeMessage(triple: (String, String, String)): (IPv4Addr, ArpCacheEntry, String) = {
//        val ip = triple._1
//        val entry = triple._2
//        val owner = triple._3
//        (IPv4Addr.fromString(ip), ArpCacheEntry.decode(entry), owner)
//    }
//
//    override def encodeMessage(opinion: (IPv4Addr, ArpCacheEntry, String)): (String, String, String) = {
//        val ip = opinion._1
//        val entry = opinion._2
//        val owner = opinion._3
//        (ip.toString(),  owner, entry.encode())
//    }
    override def decodeMessage(triple: (Key, Payload, Owner)): (IPv4Addr, ArpCacheEntry, String) = {
        val ip = triple._1
        val entry = triple._2
        val owner = triple._3
        (IPv4Addr.fromString(ip), ArpCacheEntry.decode(entry), owner)
    }

    override def encodeMessage(opinion: (IPv4Addr, ArpCacheEntry, String)): (Key, Payload, Owner) = {
        val ip = opinion._1
        val entry = opinion._2
        val owner = opinion._3
        (ip.toString(), entry.encode(), owner)
    }
}
/*
class ArpEncoder() extends Serializer[(IPv4Addr, ArpCacheEntry, String)] {
    private val serializer = new StringSerializer
    override def configure(configs: util.Map[String, _],
                           isKey: Boolean): Unit = {}
    override def serialize(topic: String,
                           data: (IPv4Addr, ArpCacheEntry, String)): Array[Byte] = {
        val ip = data._1
        val mac = data._2
        val owner = data._3
        val str = ip.toString + "-" + mac.encode() + "-" + owner
        serializer.serialize(topic, str)
    }
    override def close(): Unit = {}
}

class ArpDecoder(props: VerifiableProperties = null)
    extends Decoder[(IPv4Addr, ArpCacheEntry, String)] {

    private val strDecoder = new StringDecoder()
    override def fromBytes(bytes: Array[Byte])
    : (IPv4Addr, ArpCacheEntry, String) = {
        val str = strDecoder.fromBytes(bytes)
        val tokens = str.split("-")
        val ip = IPv4Addr.fromString(tokens(0))
        val mac = ArpCacheEntry.decode(tokens(1))
        val owner = tokens(2)
        (ip, mac, owner)
    }
}
*/