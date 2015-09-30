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

package org.midonet.cluster.storage

import java.util.concurrent.TimeUnit
import com.typesafe.config.Config


class KafkaConfig(val conf: Config) {
  /* List of Kafka brokers: host1:port1, host2:port2, host3:port3 */
  def brokers = conf.getString("kafka.brokers")
  /* Zookeeper connect string: host1:port1, host2: port2, host3: port3. */
  def zkHosts = conf.getString("kafka.zk_hosts")
  /* The number of replicas per topic. */
  def replicationFactor = conf.getInt("kafka.replication_factor")
  /* The session timeout used by the consumer with ZooKeeper */
  def zkSessionTimeout = conf.getInt("kafka.zk_session_timeout")
}