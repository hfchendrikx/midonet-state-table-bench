package org.midonet.cluster.data.storage

import java.util
import java.util.concurrent.TimeUnit

import kafka.serializer.{Decoder, StringDecoder}
import kafka.utils.VerifiableProperties
import org.I0Itec.zkclient.ZkClient
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.midonet.midolman.state.ArpCacheEntry
import org.midonet.packets.IPv4Addr
import org.midonet.util.reactivex.AwaitableObserver
import rx.observers.TestObserver

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object ArpMergedMap {
    type ArpObs = TestObserver[(IPv4Addr, ArpCacheEntry)]
        with AwaitableObserver[(IPv4Addr, ArpCacheEntry)]

    def arpOrdering() = new Ordering[ArpCacheEntry] {
        override def compare(x: ArpCacheEntry, y: ArpCacheEntry): Int = {
            (x.stale - y.stale).asInstanceOf[Int]
        }
    }

    def newArpMap(mapId: String, owner: String, zkClient: ZkClient)
    : MergedMap[IPv4Addr, ArpCacheEntry] = {
        val kafkaBus: KafkaBus[IPv4Addr, ArpCacheEntry] = new
            KafkaBus[IPv4Addr, ArpCacheEntry](mapId, owner,
                                              KafkaBus.defaultConfig(),
                                              zkClient,
                                              new ArpMergedMap())
        new MergedMap[IPv4Addr, ArpCacheEntry](kafkaBus,
                                               ArpMergedMap.arpOrdering)
    }

    def newArpMapAndReturnKafkaBus(mapId: String, owner: String, zkClient: ZkClient)
    : (MergedMap[IPv4Addr, ArpCacheEntry], KafkaBus[IPv4Addr, ArpCacheEntry]) = {
        val kafkaBus: KafkaBus[IPv4Addr, ArpCacheEntry] = new
            KafkaBus[IPv4Addr, ArpCacheEntry](mapId, owner,
                KafkaBus.defaultConfig(),
                zkClient,
                new ArpMergedMap())
        (new MergedMap[IPv4Addr, ArpCacheEntry](kafkaBus,
            ArpMergedMap.arpOrdering), kafkaBus)
    }

    def timeoutDuration(timeout: Long): FiniteDuration =
        duration.pairLongToDuration(timeout, TimeUnit.MILLISECONDS)

    def awaitForObserverEvents(obs: ArpObs, eventCount: Int, timeout: Long)
    : Boolean = {
        obs.awaitOnNext(eventCount, timeoutDuration(timeout))
    }

    def arpMapObserver(map: MergedMap[IPv4Addr, ArpCacheEntry])
    : ArpObs = {
        val obs = new TestObserver[(IPv4Addr, ArpCacheEntry)]()
            with AwaitableObserver[(IPv4Addr, ArpCacheEntry)]
        map.observable subscribe obs
        obs
    }
}

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

class ArpMergedMap extends KafkaSerialization[IPv4Addr, ArpCacheEntry] {
    /**
     * Converts a map key into a string.
     */
    override def keyAsString(key: IPv4Addr): String = key.toString

    /**
     * Returns an encoder that is used to serialize a (key, value, owner)
     * triple into an array of bytes.
     */
    override def messageEncoder = new ArpEncoder()

    /**
     * Builds a key from its String representation.
     */
    override def keyFromString(key: String): IPv4Addr = IPv4Addr.fromString(key)

    /**
     * Returns a decoder that is used to deserialize an array of bytes into
     * a (key, value, owner) triple.
     */
    override def messageDecoder = new ArpDecoder()
}
