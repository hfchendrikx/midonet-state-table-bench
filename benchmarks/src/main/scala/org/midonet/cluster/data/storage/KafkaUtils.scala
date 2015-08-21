package org.midonet.cluster.data.storage

import java.util.Properties

import kafka.server.{KafkaConfig, KafkaServer}
import org.apache.curator.test.TestingServer

object KafkaUtils {
    private val logDir = "/tmp/embeddedKafka"

    var zkServer: TestingServer = _
    var kafkaServer: KafkaServer = _

    def startKafkaServer(config: MergedMapConfig): Unit = {
        val zkPort = config.zkHosts.split(":")(1).toInt
        //zkServer = new TestingServer()

        val brokerPort = KafkaBus.brokers.split(":")(1)
        val props = new Properties()
        props.put("host.name", "localhost")
        props.put("port", brokerPort)
        props.put("broker.id", "0")
        props.put("log.dir", logDir)
        props.put("zookeeper.connect", config.zkHosts)
        props.put("log.cleaner.enable", "true")
        props.put("log.cleanup.policy", "compact")
        kafkaServer = new KafkaServer(new KafkaConfig(props))
        kafkaServer.startup()

        Thread.sleep(2000)
        kafkaServer.zkClient.waitUntilConnected()
    }

    def stopKafkaServer: Unit = {
        for (log <- kafkaServer.logManager.allLogs) {
            kafkaServer.logManager.deleteLog(log.topicAndPartition)
        }
        if (kafkaServer != null) {
            kafkaServer.shutdown()
            kafkaServer.awaitShutdown()
        }
        if (zkServer != null) {
            zkServer.close()
        }
    }
}
