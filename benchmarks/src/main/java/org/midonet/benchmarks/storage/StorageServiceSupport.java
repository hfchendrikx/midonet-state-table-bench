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

package org.midonet.benchmarks.storage;

import org.midonet.cluster.EntityIdSetEvent;
import org.midonet.cluster.data.Bridge;
import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.ports.BridgePort;
import org.midonet.cluster.data.ports.RouterPort;
import rx.Observer;
import rx.Subscription;

import java.util.List;
import java.util.UUID;

/**
 * Provides an interface for common storage-related methods
 */
public interface StorageServiceSupport {

    public static class StorageException extends Exception {
        private static final long serialVersionUID = 426007L;
        public StorageException(Throwable cause) {
            super(cause);
        }
        public StorageException(String msg) {
            super(msg);
        }
    }

    /**
     * Create an object in the storage service
     */
    <T> T create(T object) throws StorageException;

    /**
     * Delete the object of the specified class with the given id
     * @return true if the object was successfully deleted
     */
    <T> boolean delete(Class<T> clazz, UUID id) throws StorageException;

    /**
     * Update an object in the storage service
     */
    <T> void update(T object) throws StorageException;

    /**
     * Retrieve the data for the specified object
     */
    <T> T get(Class<T> clazz, UUID id) throws StorageException;

    /**
     * Retrieve all objects of the specified type
     */
    <T> List<T> getAll(Class<T> clazz) throws StorageException;

    /**
     * Subscribe to changes to the specifed object
     */
    Subscription subscribe(UUID id, Observer<Bridge> obs)
        throws StorageException;

    /**
     * Subscribe to creations of objects of the specified type.
     */
    <T> Subscription subscribeAll(Class<T> clazz,
                                  Observer<EntityIdSetEvent<UUID>> obs)
        throws StorageException;

    /**
     * Create a port attached to the specified router/bridge
     */
    RouterPort attachNewPortToRouter(UUID routerId) throws StorageException;

    BridgePort attachNewPortToBridge(UUID bridgeId) throws StorageException;

    /**
     * Get ports attached to a router/bridge
     */
    List<RouterPort> getPortsFromRouter(UUID routerId) throws StorageException;

    List<BridgePort> getPortsFromBridge(UUID bridgeId) throws StorageException;

    /**
     * Link a port to the specified peer port
     */
    void linkPorts(Port<?, ?> port, UUID peerPortId) throws StorageException;

    /**
     * Connect two routers via newly created ports
     */
    void connectToRouter(UUID routerId, UUID targetRouterId)
        throws StorageException;

    /**
     * Connect a router to a bridge via newly created ports
     */
    void connectToBridge(UUID routerId, UUID targetBridgeId)
        throws StorageException;

}
