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

package org.midonet.benchmarks.storage;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;
import rx.Subscription;

import org.midonet.cluster.DataClient;
import org.midonet.cluster.EntityIdSetEvent;
import org.midonet.cluster.EntityIdSetMonitor;
import org.midonet.cluster.EntityMonitor;
import org.midonet.cluster.data.Bridge;
import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.Router;
import org.midonet.cluster.data.ports.BridgePort;
import org.midonet.cluster.data.ports.RouterPort;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.StateAccessException;
import org.midonet.midolman.state.ZookeeperConnectionWatcher;
import org.midonet.midolman.state.zkManagers.BridgeZkManager.BridgeConfig;


/**
 * DataClient-based storage service for org.midonet.benchmarks
 */
public class DataClientStorageService implements StorageServiceSupport {

    private final static Logger log =
        LoggerFactory.getLogger(DataClientStorageService.class);

    private final DataClient dataClient;
    private final ZookeeperConnectionWatcher zkConnection;
    private EntityMonitor<UUID, BridgeConfig, Bridge> bridgeMonitor;
    private EntityIdSetMonitor<UUID> bridgeAllMonitor;

    public DataClientStorageService(DataClient dataClient,
                                    ZookeeperConnectionWatcher zkConnection) {
        this.dataClient = dataClient;
        this.zkConnection = zkConnection;
        bridgeMonitor = null;
        bridgeAllMonitor = null;
    }

    @Override
    public <T> T create(T object) throws StorageException {
        UUID id;
        try {
            if (object instanceof Router) {
                Router obj = (Router) object;
                id = dataClient.routersCreate(obj);
                obj.setId(id);
            } else if (object instanceof Bridge) {
                Bridge obj = (Bridge) object;
                id = dataClient.bridgesCreate(obj);
                obj.setId(id);
            } else if (object instanceof RouterPort) {
                RouterPort obj = (RouterPort)object;
                id = dataClient.portsCreate(obj);
                obj.setId(id);
            } else if (object instanceof BridgePort) {
                BridgePort obj = (BridgePort)object;
                id = dataClient.portsCreate(obj);
                obj.setId(id);
            } else {
                throw new UnsupportedOperationException(
                    "cannot store class " + object.getClass());
            }
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
        return object;
    }

    @Override
    public <T> void update(T object) throws StorageException {
        try {
            if (object instanceof Router) {
                Router obj = (Router) object;
                dataClient.routersUpdate(obj);
            } else if (object instanceof Bridge) {
                Bridge obj = (Bridge) object;
                dataClient.bridgesUpdate(obj);
            } else if (object instanceof BridgePort) {
                BridgePort obj = (BridgePort) object;
                dataClient.portsUpdate(obj);
            } else if (object instanceof RouterPort) {
                RouterPort obj = (RouterPort) object;
                dataClient.portsUpdate(obj);
            } else {
                throw new UnsupportedOperationException(
                    "cannot update class " + object.getClass());
            }
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public <T> boolean delete(Class<T> clazz, UUID id) throws StorageException {
        try {
            if (Router.class.isAssignableFrom(clazz)) {
                dataClient.routersDelete(id);
            } else if (Bridge.class.isAssignableFrom(clazz)) {
                dataClient.bridgesDelete(id);
            } else if (Port.class.isAssignableFrom(clazz)) {
                dataClient.portsDelete(id);
            } else {
                throw new UnsupportedOperationException(
                    "cannot delete class " + clazz);
            }
            return true;
        } catch(StateAccessException | SerializationException e) {
            return false;
        }
    }

    @Override
    public <T> T get(Class<T> clazz, UUID id) throws StorageException {
        T object = null;
        try {
            if (clazz.isAssignableFrom(Router.class)) {
                object = clazz.cast(dataClient.routersGet(id));
            } else if (clazz.isAssignableFrom(Bridge.class)) {
                object = clazz.cast(dataClient.bridgesGet(id));
            } else if (clazz.isAssignableFrom(BridgePort.class)) {
                Port<?, ?> p = dataClient.portsGet(id);
                object = clazz.cast(p);
            } else if (clazz.isAssignableFrom(RouterPort.class)) {
                Port<?, ?> p = dataClient.portsGet(id);
                object = clazz.cast(p);
            } else {
                throw new UnsupportedOperationException(
                    "cannot get class " + clazz);
            }
        } catch(StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
        return object;
    }

    @Override
    public <T> List<T> getAll(Class<T> clazz) throws StorageException {
        List<T> list;
        try {
            if (clazz.isAssignableFrom(Router.class)) {
                list = (List<T>) dataClient.routersGetAll();
            } else if (clazz.isAssignableFrom(Bridge.class)) {
                list = (List<T>) dataClient.bridgesGetAll();
            } else if (clazz.isAssignableFrom(Port.class)) {
                list = (List<T>) dataClient.portsGetAll();
            } else {
                throw new UnsupportedOperationException(
                    "cannot get object list for class " + clazz);
            }
        } catch(StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
        return list;
    }

    public Subscription subscribe(UUID id, Observer<Bridge> obs)
        throws StorageException {
        if (bridgeMonitor == null) {
            bridgeMonitor = dataClient.bridgesGetMonitor(zkConnection);
        }
        Observable<Bridge> stream = bridgeMonitor.updated();
        Subscription subs = stream.subscribe(obs);
        bridgeMonitor.watch(id);
        return subs;
    }

    public <T> Subscription subscribeAll(Class<T> clazz,
                                         Observer<EntityIdSetEvent<UUID>> obs)
        throws StorageException  {
        Subscription subs;
        try {
            if (clazz.isAssignableFrom(Bridge.class)) {
                if (bridgeAllMonitor == null) {
                    bridgeAllMonitor =
                        dataClient.bridgesGetUuidSetMonitor(zkConnection);
                }
                Observable<EntityIdSetEvent<UUID>> stream =
                    bridgeAllMonitor.getObservable();
                subs = stream.subscribe(obs);
                bridgeAllMonitor.notifyState();
            } else {
                throw new UnsupportedOperationException(
                    "cannot subscribe to class " + clazz);
            }
        } catch (StateAccessException e) {
            throw new StorageException(e);
        }
        return subs;
    }

    @Override
    public RouterPort attachNewPortToRouter(UUID routerId)
        throws StorageException {
        RouterPort port = new RouterPort();
        port.setDeviceId(routerId);
        UUID portId;
        try {
            portId = dataClient.portsCreate(port);
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
        port.setId(portId);
        return port;
    }

    @Override
    public BridgePort attachNewPortToBridge(UUID bridgeId)
        throws StorageException {
        BridgePort port = new BridgePort();
        port.setDeviceId(bridgeId);
        UUID portId;
        try {
            portId = dataClient.portsCreate(port);
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
        port.setId(portId);
        return port;
    }

    @Override
    public List<RouterPort> getPortsFromRouter(UUID routerId)
        throws StorageException {
        try {
            return (List<RouterPort>)(List<?>)
                dataClient.portsFindByRouter(routerId);
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public List<BridgePort> getPortsFromBridge(UUID bridgeId)
        throws StorageException {
        try {
            return dataClient.portsFindByBridge(bridgeId);
        } catch (StateAccessException | SerializationException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void linkPorts(Port<?, ?> port, UUID peerPortId) throws StorageException {
        port.setPeerId(peerPortId);
        update(port);
    }

    @Override
    public void connectToRouter(UUID routerId, UUID targetRouterId)
        throws StorageException {
        RouterPort router1Port = attachNewPortToRouter(routerId);
        RouterPort router2Port = attachNewPortToRouter(targetRouterId);
        linkPorts(router1Port, router2Port.getId());
    }

    @Override
    public void connectToBridge(UUID routerId, UUID targetBridgeId)
        throws StorageException {
        RouterPort routerPort = attachNewPortToRouter(routerId);
        BridgePort bridgePort = attachNewPortToBridge(targetBridgeId);
        linkPorts(routerPort, bridgePort.getId());
    }
}
