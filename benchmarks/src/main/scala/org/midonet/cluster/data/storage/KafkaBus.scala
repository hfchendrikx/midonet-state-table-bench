/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.data.storage

import java.util.Properties
import java.util.concurrent.Executors

import com.google.common.annotations.VisibleForTesting
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import kafka.admin.AdminUtils
import kafka.consumer.{ConsumerConfig, ConsumerIterator}
import kafka.javaapi.consumer.ConsumerConnector
import kafka.serializer.{Decoder, StringDecoder}
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.midonet.util.functors.makeRunnable
import org.slf4j.LoggerFactory
import rx.Observer
import rx.subjects.PublishSubject

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

trait KafkaSerialization[K, V >: Null <: AnyRef] {
  /**
   * Converts a map key into a string.
   */
  def keyAsString(key: K): String

  /**
   * Builds a key from its String representation.
   */
  def keyFromString(key: String): K

  /**
   * Returns an encoder that is used to serialize a (key, value, owner)
   * triple into an array of bytes.
   */
  def messageEncoder: Serializer[(K, V, String)]

  /**
   * Returns a decoder that is used to deserialize an array of bytes into
   * a (key, value, owner) triple.
   */
  def messageDecoder: Decoder[(K, V, String)]
}

object KafkaBus {
  val zkHosts = System.getProperty("zk.hosts")
  val brokers = System.getProperty("kafka.hosts")

  def defaultConfig(): MergedMapConfig = {
    val props = new Properties()
    props.put("kafka.brokers", brokers)
    props.put("kafka.zk.hosts", zkHosts)
    props.put("kafka.replication.factor", "3")
    new MergedMapConfig(ConfigFactory.parseProperties(props))
  }

  val zookeeperClient = new ZkClient(KafkaBus.zkHosts,
    5000 /*session timeout*/,
    5000 /*connection timeout*/,
    ZKStringSerializer)
}

/**
 * A Kafka-based implementation of the MergedMapStorage trait.
 *
 * An opinion for key K and value V from an owner O is published as a kafka
 * message with key K-O, and value K-V-O. By using Kafka's log compaction
 * feature we only keep the latest message for each kafka key in the log.
 * This means that we keep the latest opinion for each owner and key.
 * //TODO: Garbage collect opinions of owners that have left.
 */
class KafkaBus[K, V >: Null <: AnyRef](id: String, ownerId: String,
                                       config: MergedMapConfig,
                                       zkClient: ZkClient,
                                       kafkaIO: KafkaSerialization[K, V])
  extends MergedMapBus[K, V] {

  type Opinion = (K, V, String)

  private val log =
    Logger(LoggerFactory.getLogger(getClass.getName + "-" + mapId.toString))

  private val bufferSize = 10000
  private val inputSubj = PublishSubject.create[Opinion]()
  private val opinionInput =
    inputSubj.onBackpressureBuffer()
  private val outputSubj = PublishSubject.create[Opinion]()

  //Topics are automatically created if they do no exist
  createTopicIfNeeded(config)
  private val producer = createProducer(config)
  private val consumer = createConsumer(config)
  private val consumerIterator = createConsumerIt(consumer)

  /* The consumer */
  private val consumerThread = Executors.newSingleThreadExecutor()
  private val consumerRunnable = makeRunnable({
    try {
      while (consumerIterator.hasNext()) {
        val msg = consumerIterator.next
        inputSubj onNext msg.message
      }
    } catch {
      case NonFatal(e) =>
        log.warn("Caught exception in consumer thread, shutting down.", e)
        //shutdown()
    }
  })
  consumerThread.submit(consumerRunnable)

  private val sendCallBack = new Callback() {
    def onCompletion(metadata: RecordMetadata, e: Exception): Unit = {
      if (e != null) {
        if (metadata != null) {
          log.warn("Unable to send Kafka message for topic: {} and " +
            "partition: {}", metadata.topic,
            Int.box(metadata.partition), e)
        } else {
          log.warn("Unable to send Kafka message (metadata is null) ", e)
        }
      }
    }
  }

  def shutdown(): Unit = {
    producer.close()
    consumerThread.shutdownNow()
    consumer.shutdown()
  }

  /* The producer */
  private val producerObserver = new Observer[Opinion] {
    override def onCompleted(): Unit = {
      log.info("Kafka bus output completed, shutting down.")
      shutdown()
    }
    override def onError(e: Throwable): Unit = {
      log.warn("Error on output kafka bus, shutting down.", e)
      shutdown()
    }
    override def onNext(opinion: Opinion): Unit = {
      val msgKey = kafkaIO.keyAsString(opinion._1) + "-" + ownerId
      val msg =
        new ProducerRecord[String, Opinion](mapId.toString /*topic*/,
          msgKey /*key*/,
          opinion /*value*/)
      producer.send(msg, sendCallBack)
    }
  }
  outputSubj.onBackpressureBuffer() subscribe producerObserver

  private def createTopicIfNeeded(config: MergedMapConfig): Unit = {
    if (!AdminUtils.topicExists(zkClient, topic = mapId.toString)) {
      val props = new Properties()
      // Always keep the last message for each key in the log.
      props.put("cleanup.policy", "compact")
      props.put("min.insync.replicas",
        computeMajority(config.replicationFactor).toString)
      AdminUtils
        .createTopic(zkClient, topic = mapId.toString, partitions = 1,
          config.replicationFactor, props)
    }
  }

  private def createConsumer(config: MergedMapConfig)
  : ConsumerConnector = {
    val consProps = new Properties
    consProps.put("zookeeper.connect", config.zkHosts)
    consProps.put("group.id", "group-" + mapId + "-" + owner)
    // Configure the consumer such that it can read all messages in
    // the topic (also those published before subscribing to the topic).
    consProps.put("auto.offset.reset", "smallest")
    kafka.consumer.Consumer.createJavaConsumerConnector(
      new ConsumerConfig((consProps)))
  }

  @VisibleForTesting
  private[storage] def computeMajority(replicaCount: Int): Int =
    Math.ceil((replicaCount + 1.0d) / 2.0d).asInstanceOf[Int]

  private def createProducer(config: MergedMapConfig)
  : KafkaProducer[String, Opinion] = {
    val prodProps = new Properties()
    prodProps.put("bootstrap.servers", config.brokers)
    /* The number of acks the producer requires the broker to have received
       from the replicas before considering a request complete */
    prodProps.put("acks", "all")
    prodProps.put("key.serializer", classOf[StringSerializer].getName)
    prodProps.put("value.serializer",
      kafkaIO.messageEncoder.getClass.getName)
    new KafkaProducer[String, Opinion](prodProps)
  }

  private def createConsumerIt(cons: ConsumerConnector)
  : ConsumerIterator[String, Opinion] = {
    val topicCountMap = Map(mapId.toString -> Int.box(1) /* #partitions */)
      .asJava
    val consumerMap = cons.createMessageStreams(topicCountMap,
      new StringDecoder(),
      kafkaIO.messageDecoder)
    val stream =  consumerMap.get(mapId.toString).get(0)
    stream.iterator()
  }

  override def opinionObservable = opinionInput
  override def opinionObserver = outputSubj

  /**
   * @return The map id this storage corresponds to.
   */
  override def mapId: String = id

  /**
   * @return The owner this storage is built for.
   */
  override def owner: String = ownerId
}

