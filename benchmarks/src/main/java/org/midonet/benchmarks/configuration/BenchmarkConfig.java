/*
 * Copyright 2014 Midokura SARL
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
package org.midonet.benchmarks.configuration;

import org.midonet.cluster.config.ZookeeperConfig;
import org.midonet.config.ConfigBool;
import org.midonet.config.ConfigGroup;
import org.midonet.config.ConfigInt;
import org.midonet.config.ConfigString;

@ConfigGroup(BenchmarkConfig.GROUP_NAME)
public interface BenchmarkConfig extends ZookeeperConfig, MpiConfig {

    public final static String GROUP_NAME = "benchmarks";

    /**
     * Defines the platform where the benchmark is being executed
     * (e.g. "midocloud")
     */
    @ConfigString(key = "testbed", defaultValue = "localhost")
    public String getTestbed();

    /**
     * Defines the benchmark topology (number of tenants)
     */
    @ConfigInt(key = "topology_tenants", defaultValue = 1)
    public int getTopologyTenants();

    /**
     * Defines the benchmark topology (number of bridges per tenant)
     */
    @ConfigInt(key = "topology_bridges_per_tenant", defaultValue = 1)
    public int getTopologyBridgesPerTenant();

    /**
     * Defines the benchmark topology (number of ports per bridge)
     */
    @ConfigInt(key = "topology_ports_per_bridge", defaultValue = 1)
    public int getTopologyPortsPerBridge();

    /**
     * Number of notifications to generate for testing subscribers
     */
    @ConfigInt(key = "notification_count", defaultValue = 1)
    public int getNotificationCount();

    /**
     * Indicates how many times to repeat read/write operations at each phase
     */
    @ConfigInt(key = "repeat", defaultValue = 1)
    public int getRepeat();

    /**
     * Get the output file for benchmark results (default goes stdout)
     */
    @ConfigString(key = "bench_output_file", defaultValue = "")
    public String getOutput();

    /**
     * Get the output file for binary protobuf results
     */
    @ConfigString(key = "bench_output_bin", defaultValue = "")
    public String getBinOutput();

    /**
     * Skip the build layout phase
     */
    @ConfigBool(key = "skip_build_layout", defaultValue = false)
    public boolean getSkipBuildLayout();

    /**
     * Skip the layout removal phase
     */
    @ConfigBool(key = "skip_remove_layout", defaultValue = false)
    public boolean getSkipRemoveLayout();

    /**
     * Skip the disjoint read phase
     */
    @ConfigBool(key = "skip_test_read", defaultValue = false)
    public boolean getSkipTestRead();

    /**
     * Skip the all-to-all read phase
     */
    @ConfigBool(key = "skip_test_read_alltoall", defaultValue = false)
    public boolean getSkipTestReadAllToAll();

    /**
     * Skip the disjoint bridge update phase
     */
    @ConfigBool(key = "skip_test_update_bridges", defaultValue = false)
    public boolean getSkipTestUpdateBridges();

    /**
     * Skip the all-to-all bridge update phase
     */
    @ConfigBool(key = "skip_test_update_bridges_alltoall", defaultValue = false)
    public boolean getSkipTestUpdateBridgesAllToAll();

    /**
     * Skip the disjoint bridge update phase
     */
    @ConfigBool(key = "skip_test_update_ports", defaultValue = false)
    public boolean getSkipTestUpdatePorts();

    /**
     * Skip the all-to-all bridge update phase
     */
    @ConfigBool(key = "skip_test_update_ports_alltoall", defaultValue = false)
    public boolean getSkipTestUpdatePortsAllToAll();

    /**
     * Skip the single bridge update notification phase
     */
    @ConfigBool(key = "skip_test_notify_bridge", defaultValue = false)
    public boolean getSkipTestNotifyBridge();

    /**
     * Skip the bridge creation/deletion notification phase
     */
    @ConfigBool(key = "skip_test_notify_bridge_creation", defaultValue = false)
    public boolean getSkipTestNotifyBridgeCreation();

    /**
     * Skip the bridge chunk read phase
     */
    @ConfigBool(key = "skip_test_read_bridges_chunk", defaultValue = false)
    public boolean getSkipTestReadBridgesChunk();

    /**
     * Skip the port chunk read phase
     */
    @ConfigBool(key = "skip_test_read_ports_chunk", defaultValue = false)
    public boolean getSkipTestReadPortsChunk();
}
