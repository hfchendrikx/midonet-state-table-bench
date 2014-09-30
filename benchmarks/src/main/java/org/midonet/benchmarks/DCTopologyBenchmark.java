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
package org.midonet.benchmarks;

import java.nio.file.Path;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import mpi.MPI;
import mpi.MPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.benchmarks.configuration.BenchmarkConfig;
import org.midonet.benchmarks.configuration.ConfigException;
import org.midonet.benchmarks.storage.DataClientStorageService;
import org.midonet.cluster.DataClient;
import org.midonet.cluster.services.MidostoreSetupService;
import org.midonet.config.ConfigProvider;
import org.midonet.midolman.guice.cluster.DataClusterClientModule;
import org.midonet.midolman.guice.cluster.MidostoreModule;
import org.midonet.midolman.guice.config.ConfigProviderModule;
import org.midonet.midolman.guice.serialization.SerializationModule;
import org.midonet.midolman.guice.zookeeper.ZookeeperConnectionModule;
import org.midonet.midolman.state.ZookeeperConnectionWatcher;
import org.midonet.midolman.version.guice.VersionModule;

/**
 * This is the main application that starts the benchmark
 * measuring topology creation, access and change notification
 * times, based on the DataClient storage engine.
 */
public class DCTopologyBenchmark extends TopologyBenchmark {
    private static final Logger log =
        LoggerFactory.getLogger(DCTopologyBenchmark.class);

    private static final String BENCHNAME = "DCTopologyBenchmark";

    private static Injector createInjector(Path configFilePath) {
        AbstractModule benchModule = new AbstractModule() {
            @Override
            protected void configure() {
                requireBinding(ConfigProvider.class);
                install(new ZookeeperConnectionModule());
                install(new MidostoreModule());
                install(new DataClusterClientModule());
                install(new VersionModule());
                install(new SerializationModule());
            }
        };
        return Guice.createInjector(
            new ConfigProviderModule(configFilePath.toString()), benchModule);
    }

    protected DCTopologyBenchmark(String[] args)
        throws MPIException, ConfigException {
        this(MPI.COMM_WORLD.getSize(), MPI.COMM_WORLD.getRank(),
             createInjector(getCfgFilePath(args)), BENCHNAME);
    }

    protected DCTopologyBenchmark(int mpiSize, int mpiRank, Injector injector,
                                  String benchName)
        throws MPIException, ConfigException {
        super(mpiSize, mpiRank, injector.getInstance(ConfigProvider.class)
              .getConfig(BenchmarkConfig.class), benchName);
        injector.getInstance(MidostoreSetupService.class)
                .startAsync()
                .awaitRunning();

        DataClient dataClient = injector.getInstance(DataClient.class);
        ZookeeperConnectionWatcher zkConnection =
            injector.getInstance(ZookeeperConnectionWatcher.class);
        store = new DataClientStorageService(dataClient, zkConnection);
    }

    public static void main(String[] args) throws MPIException {
        int exit_code = 0;
        MPI.Init(args);
        try {
            DCTopologyBenchmark bench = new DCTopologyBenchmark(args);
            bench.run(args);
        } catch (Exception e) {
            log.error("application terminated abnormally", e);
            exit_code = -1;
        } finally {
            MPI.Finalize();
        }
        System.exit(exit_code);
    }
}
