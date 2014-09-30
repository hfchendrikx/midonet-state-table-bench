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

import org.midonet.config.ConfigGroup;
import org.midonet.config.ConfigString;

@ConfigGroup(MpiConfig.GROUP_NAME)
public interface MpiConfig {

    public static final String GROUP_NAME = "mpi";
    public static final String DEFAULT_HOSTS = "127.0.0.1";

    /**
     * String of comma-separated hosts addresses where the processes
     * should run. If a host appears several times, MPI will spawn as
     * many processes in that host.
     */
    @ConfigString(key = "mpi_hosts", defaultValue = DEFAULT_HOSTS)
    public String getMpiHosts();
}
