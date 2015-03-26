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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import org.midonet.cluster.EntityIdSetEvent;
import org.midonet.cluster.data.Bridge;
import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.Router;
import org.midonet.cluster.data.ports.BridgePort;
import org.midonet.cluster.data.ports.RouterPort;
import org.midonet.cluster.data.storage.ReferenceConflictException;
import org.midonet.cluster.data.storage.ZookeeperObjectMapper;
import org.midonet.cluster.models.Commons;
import org.midonet.cluster.models.Topology;
import org.midonet.cluster.util.IPAddressUtil;
import org.midonet.cluster.util.UUIDUtil;
import org.midonet.packets.MAC;

import scala.collection.JavaConversions;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * DataClient-based storage service for org.midonet.benchmarks
 */
public class ZoomStorageService implements StorageServiceSupport {

    private final static Logger log =
        LoggerFactory.getLogger(ZoomStorageService.class);

    private final ZookeeperObjectMapper zoomClient;

    private final Subject<EntityIdSetEvent<UUID>, EntityIdSetEvent<UUID>>
        brStream = PublishSubject.create();

    public ZoomStorageService(ZookeeperObjectMapper zoomClient) {
        this.zoomClient = zoomClient;
    }

    private UUID fromProto(Commons.UUID uuid) {
        return UUIDUtil.fromProto(uuid);
    }

    private Commons.UUID toProto(UUID uuid) {
        return UUIDUtil.toProto(uuid);
    }

    private Router fromProto(Topology.Router router) {
        Router rt = new Router();
        if (router.hasId())
            rt.setId(fromProto(router.getId()));
        if (router.hasName())
            rt.setName(router.getName());
        if (router.hasInboundFilterId())
            rt.setInboundFilter(fromProto(router.getInboundFilterId()));
        if (router.hasOutboundFilterId())
            rt.setOutboundFilter(fromProto(router.getOutboundFilterId()));
        if (router.hasLoadBalancerId())
            rt.setLoadBalancer(fromProto(router.getLoadBalancerId()));
        if (router.hasAdminStateUp())
            rt.setAdminStateUp(router.getAdminStateUp());
        return rt;
    }

    private Topology.Router toProto(Router router) {
        Topology.Router.Builder rt = Topology.Router.newBuilder();
        rt.setId(toProto(router.getId()));
        if (router.getName() != null)
            rt.setName(router.getName());
        if (router.getInboundFilter() != null)
            rt.setInboundFilterId(toProto(router.getInboundFilter()));
        if (router.getOutboundFilter() != null)
            rt.setOutboundFilterId(toProto(router.getOutboundFilter()));
        if (router.getLoadBalancer() != null)
            rt.setLoadBalancerId(toProto(router.getLoadBalancer()));
        rt.setAdminStateUp(router.isAdminStateUp());
        return rt.build();
    }

    private Bridge fromProto(Topology.Network bridge) {
        Bridge br = new Bridge();
        if (bridge.hasId())
            br.setId(fromProto(bridge.getId()));
        if (bridge.hasName())
            br.setName(bridge.getName());
        if (bridge.hasInboundFilterId())
            br.setInboundFilter(fromProto(bridge.getInboundFilterId()));
        if (bridge.hasOutboundFilterId())
            br.setOutboundFilter(fromProto(bridge.getOutboundFilterId()));
        if (bridge.hasTunnelKey())
            br.setTunnelKey((int) bridge.getTunnelKey());
        if (bridge.hasAdminStateUp())
            br.setAdminStateUp(bridge.getAdminStateUp());
        if (bridge.hasTenantId())
            br.setProperty(Bridge.Property.tenant_id, bridge.getTenantId());
        return br;
    }

    private Topology.Network toProto(Bridge bridge) {
        Topology.Network.Builder br = Topology.Network.newBuilder();
        br.setId(toProto(bridge.getId()));
        if (bridge.getName() != null)
            br.setName(bridge.getName());
        if (bridge.getInboundFilter() != null)
            br.setInboundFilterId(toProto(bridge.getInboundFilter()));
        if (bridge.getOutboundFilter() != null)
            br.setOutboundFilterId(toProto(bridge.getOutboundFilter()));
        br.setTunnelKey(bridge.getTunnelKey());
        br.setAdminStateUp(bridge.isAdminStateUp());
        if (bridge.getProperty(Bridge.Property.tenant_id) != null)
            br.setTenantId(bridge.getProperty(Bridge.Property.tenant_id));
        return br.build();
    }

    private void fromProto(Port<?, ?> p, Topology.Port port) {
        if (port.hasId())
            p.setId(fromProto(port.getId()));
        if (port.hasAdminStateUp())
            p.setAdminStateUp(port.getAdminStateUp());
        if (port.hasHostId())
            p.setHostId(fromProto(port.getHostId()));
        if (port.hasInboundFilterId())
            p.setInboundFilter(fromProto(port.getInboundFilterId()));
        if (port.hasOutboundFilterId())
            p.setOutboundFilter(fromProto(port.getOutboundFilterId()));
        if (port.hasInterfaceName())
            p.setInterfaceName(port.getInterfaceName());
        if (port.hasPeerId())
            p.setPeerId(fromProto(port.getPeerId()));
        if (port.hasTunnelKey())
            p.setTunnelKey((int) port.getTunnelKey());
        if (port.hasVifId())
            p.setProperty(Port.Property.vif_id,
                          fromProto(port.getVifId()).toString());
    }

    private BridgePort fromProtoBridgePort(Topology.Port port) {
        BridgePort p = new BridgePort();
        fromProto(p, port);
        if (port.hasNetworkId())
            p.setDeviceId(fromProto(port.getNetworkId()));
        if (port.hasVlanId())
            p.setVlanId((short)port.getVlanId());
        return p;
    }

    private RouterPort fromProtoRouterPort(Topology.Port port) {
        RouterPort p = new RouterPort();
        fromProto(p, port);
        if (port.hasRouterId())
            p.setDeviceId(fromProto(port.getRouterId()));
        if (port.hasPortMac())
            p.setHwAddr(MAC.fromString(port.getPortMac()));
        if (port.hasPortAddress())
            p.setPortAddr(IPAddressUtil.toIPv4Addr(port.getPortAddress())
                                                       .toString());
        return p;
    }

    private void toProto(Topology.Port.Builder p, Port<?, ?> port) {
        p.setId(toProto(port.getId()));
        p.setAdminStateUp(port.isAdminStateUp());
        if (port.getHostId() != null)
            p.setHostId(toProto(port.getHostId()));
        if (port.getInboundFilter() != null)
            p.setInboundFilterId(toProto(port.getInboundFilter()));
        if (port.getOutboundFilter() != null)
            p.setOutboundFilterId(toProto(port.getOutboundFilter()));
        if (port.getInterfaceName() != null)
            p.setInterfaceName(port.getInterfaceName());
        if (port.getPeerId() != null)
            p.setPeerId(toProto(port.getPeerId()));
        p.setTunnelKey(port.getTunnelKey());
        if (port.getProperty(Port.Property.vif_id) != null)
            p.setVifId(toProto(UUID.fromString(
                port.getProperty(Port.Property.vif_id))));
    }

    private Topology.Port toProto(BridgePort port) {
        Topology.Port.Builder p = Topology.Port.newBuilder();
        toProto(p, port);
        if (port.getDeviceId() != null)
            p.setNetworkId(toProto(port.getDeviceId()));
        if (port.getVlanId() != null)
            p.setVlanId(port.getVlanId());
        return p.build();
    }

    private Topology.Port toProto(RouterPort port) {
        Topology.Port.Builder p = Topology.Port.newBuilder();
        toProto(p, port);
        if (port.getDeviceId() != null)
            p.setRouterId(toProto(port.getDeviceId()));
        if (port.getHwAddr() != null)
            p.setPortMac(port.getHwAddr().toString());
        if (port.getPortAddr() != null)
            p.setPortAddress(IPAddressUtil.toProto(port.getPortAddr()));
        return p.build();
    }

    /* TODO: Protobuf-based classes should be registered instead of real ones
     * The CRUD methods should do the conversion...
     */
    public void registerClasses() {
        if (!zoomClient.isBuilt()) {
            zoomClient.registerClass(Topology.Router.class);
            zoomClient.registerClass(Topology.Network.class);
            zoomClient.registerClass(Topology.Port.class);
            zoomClient.build();
        }
    }

    @Override
    public <T> T create(T object) throws StorageException {
        UUID id = UUID.randomUUID();
        try {
            if (object instanceof Router) {
                Router obj = (Router) object;
                obj.setId(id);
                zoomClient.create(toProto(obj));
            } else if (object instanceof Bridge) {
                Bridge obj = (Bridge) object;
                obj.setId(id);
                zoomClient.create(toProto(obj));
            } else if (object instanceof RouterPort) {
                RouterPort obj = (RouterPort)object;
                obj.setId(id);
                zoomClient.create(toProto(obj));
            } else if (object instanceof BridgePort) {
                BridgePort obj = (BridgePort)object;
                obj.setId(id);
                zoomClient.create(toProto(obj));
            } else {
                throw new UnsupportedOperationException(
                    "cannot store class " + object.getClass());
            }
        } catch (ReferenceConflictException e) {
            throw new StorageException(e);
        }
        return object;
    }

    @Override
    public <T> void update(T object) throws StorageException {
        if (object instanceof Router) {
            zoomClient.update(toProto((Router)object));
        } else if (object instanceof Bridge) {
            zoomClient.update(toProto((Bridge)object));
        } else if (object instanceof RouterPort) {
            zoomClient.update(toProto((RouterPort)object));
        } else if (object instanceof BridgePort) {
            zoomClient.update(toProto((BridgePort)object));
        } else {
            throw new UnsupportedOperationException(
                "cannot update class " + object.getClass());
        }
    }

    @Override
    public <T> boolean delete(Class<T> clazz, UUID id) throws StorageException {
        if (Router.class.isAssignableFrom(clazz)) {
            zoomClient.delete(Topology.Router.class, toProto(id));
        } else if (Bridge.class.isAssignableFrom(clazz)) {
            zoomClient.delete(Topology.Network.class, toProto(id));
        } else if (Port.class.isAssignableFrom(clazz)) {
            zoomClient.delete(Topology.Port.class, toProto(id));
        } else {
            throw new UnsupportedOperationException(
                "cannot delete class " + clazz);
        }
        return true;
    }

    @Override
    public <T> T get(Class<T> clazz, UUID id) throws StorageException {
        try {
            if (Router.class.isAssignableFrom(clazz)) {
                return (T) fromProto(Await.result(
                    zoomClient.get(Topology.Router.class, toProto(id)),
                    Duration.Inf()));
            } else if (Bridge.class.isAssignableFrom(clazz)) {
                return (T) fromProto(Await.result(
                    zoomClient.get(Topology.Network.class, toProto(id)),
                    Duration.Inf()));
            } else if (RouterPort.class.isAssignableFrom(clazz)) {
                return (T) fromProtoRouterPort(Await.result(
                    zoomClient.get(Topology.Port.class, toProto(id)),
                    Duration.Inf()));
            } else if (BridgePort.class.isAssignableFrom(clazz)) {
                return (T) fromProtoBridgePort(Await.result(
                    zoomClient.get(Topology.Port.class, toProto(id)),
                    Duration.Inf()));
            } else if (Port.class.isAssignableFrom(clazz)) {
                Topology.Port p = Await.result(
                    zoomClient.get(Topology.Port.class, toProto(id)),
                    Duration.Inf());
                if (p.hasNetworkId()) {
                    return (T) fromProtoBridgePort(p);
                } else {
                    return (T) fromProtoRouterPort(p);
                }
            } else {
                throw new UnsupportedOperationException(
                    "cannot get class " + clazz);
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    private <P> List<P> getAllAsList(Class<P> clazz)
        throws StorageException {
        try {
            return JavaConversions.seqAsJavaList(Await.result(
                zoomClient.getAll(clazz), Duration.Inf()));
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public <T> List<T> getAll(Class<T> clazz) throws StorageException {
        try {
            if (Router.class.isAssignableFrom(clazz)) {
                 return (List<T>) getAllAsList(Topology.Router.class);
            } else if (Bridge.class.isAssignableFrom(clazz)) {
                return (List<T>) getAllAsList(Topology.Network.class);
            } else if (clazz.isAssignableFrom(Port.class)) {
                List<Topology.Port> src = getAllAsList(Topology.Port.class);
                List<T> list = new ArrayList(src.size());
                for (Topology.Port p : src) {
                    if (p.hasNetworkId()) {
                        list.add((T) fromProtoBridgePort(p));
                    } else {
                        list.add((T) fromProtoRouterPort(p));
                    }
                }
                return list;
            } else {
                throw new UnsupportedOperationException(
                    "cannot get all from class " + clazz);
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Subscription subscribe(UUID id, final Observer<Bridge> obs)
        throws StorageException {
        Observer<Topology.Network> wrapper = new Observer<Topology.Network>() {
            @Override
            public void onError(Throwable e)  {
                obs.onError(e);
            }
            @Override
            public void onCompleted() {
                obs.onCompleted();
            }
            @Override
            public void onNext(Topology.Network bridge) {
                obs.onNext(fromProto(bridge));
            }
        };
        return zoomClient.observable(Topology.Network.class, toProto(id))
            .subscribe(wrapper);
    }

    @Override
    public <T> Subscription subscribeAll(Class<T> clazz,
                                         Observer<EntityIdSetEvent<UUID>> obs)
        throws StorageException  {
        // Note: the following code emulates the behaviour of the
        // original DataClient entity monitors using Zoom, to make
        // it compatible with the benchmark code. The mapping is
        // enough for benchmarking, but it does not cover all possible
        // situations. In particular, it publishes the existing state
        // as creation events instead of state events. For benchmark
        // purposes, some sleep time can be used to purge the existing
        // state.
        // Bottom line: don't use this code outside the benchmark
        Subscription subs;
        if (clazz.isAssignableFrom(Bridge.class)) {
            Observer<Observable<Topology.Network>> mon =
                new Observer<Observable<Topology.Network>>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable throwable) {
                    brStream.onError(throwable);
                }

                @Override
                public void onNext(Observable<Topology.Network> tObservable) {
                    Observer<Topology.Network> item =
                        new Observer<Topology.Network>() {
                        private UUID id = null;
                        @Override
                        public void onCompleted() {
                            if (id != null) {
                                brStream.onNext(EntityIdSetEvent.delete(id));
                            }
                        }
                        @Override
                        public void onError(Throwable throwable) {
                            brStream.onError(throwable);
                        }
                        @Override
                        public void onNext(Topology.Network t) {
                            if (id == null) {
                                id = fromProto(t.getId());
                                brStream.onNext(EntityIdSetEvent.create(id));
                            }
                        }
                    };
                    tObservable.subscribe(item);
                }
            };
            subs = brStream.subscribe(obs);
            zoomClient.observable(Topology.Network.class).subscribe(mon);
        } else {
            throw new UnsupportedOperationException(
                "cannot subscribe to class " + clazz);
        }
        return subs;
    }

    @Override
    public RouterPort attachNewPortToRouter(UUID routerId)
        throws StorageException {
        RouterPort port = new RouterPort();
        port.setDeviceId(routerId);
        try {
            create(port);
        } catch (ReferenceConflictException e) {
            throw new StorageException(e);
        }
        return port;
    }

    @Override
    public BridgePort attachNewPortToBridge(UUID bridgeId)
        throws StorageException {
        BridgePort port = new BridgePort();
        port.setDeviceId(bridgeId);
        try {
            create(port);
        } catch (ReferenceConflictException e) {
            throw new StorageException(e);
        }
        return port;
    }

    @Override
    public List<RouterPort> getPortsFromRouter(UUID routerId)
        throws StorageException {
        try {
            Commons.UUID id = toProto(routerId);
            List<Topology.Port> src = getAllAsList(Topology.Port.class);
            List<RouterPort> list = new ArrayList<>();
            for (Topology.Port p : src) {
                if (p.hasRouterId() && p.getRouterId() == id) {
                    list.add(fromProtoRouterPort(p));
                }
            }
            return list;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public List<BridgePort> getPortsFromBridge(UUID bridgeId)
        throws StorageException {
        try {
            Commons.UUID id = toProto(bridgeId);
            List<Topology.Port> src = getAllAsList(Topology.Port.class);
            List<BridgePort> list = new ArrayList<>();
            for (Topology.Port p : src) {
                if (p.hasNetworkId() && p.getNetworkId() == id) {
                    list.add(fromProtoBridgePort(p));
                }
            }
            return list;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
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
