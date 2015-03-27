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

import java.util.Arrays;
import java.util.UUID;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.midonet.cluster.config.ZookeeperConfig;
import org.midonet.cluster.data.Bridge;
import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.Router;
import org.midonet.cluster.data.ports.BridgePort;
import org.midonet.cluster.data.ports.RouterPort;
import org.midonet.cluster.data.storage.StorageWithOwnership;
import org.midonet.cluster.data.storage.ZookeeperObjectMapper;
import org.midonet.cluster.services.MidonetBackend;
import org.midonet.cluster.storage.MidonetBackendTestModule;
import org.midonet.config.ConfigProvider;
import org.midonet.midolman.cluster.LegacyClusterModule;
import org.midonet.midolman.cluster.config.ConfigProviderModule;
import org.midonet.midolman.cluster.serialization.SerializationModule;
import org.midonet.midolman.cluster.zookeeper.MockZookeeperConnectionModule;
import org.midonet.midolman.cluster.zookeeper.ZookeeperConnectionModule;
import org.midonet.midolman.state.ZookeeperConnectionWatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.midonet.benchmarks.storage.StorageServiceSupport.StorageException;

public class ZoomStorageTest {

    private static final String zkRoot = "/test/midobench";

    private Injector injector = null;

    private HierarchicalConfiguration fillConfig(
        HierarchicalConfiguration config) {
        config.addNodes(ZookeeperConfig.GROUP_NAME,
                        Arrays.asList(new HierarchicalConfiguration.Node(
                            "midolman_root_key", zkRoot)));
        return config;
    }

    private ZoomStorageService store;
    private Router router;
    private Bridge bridge;

    private static Injector createTestInjector(HierarchicalConfiguration config) {
        AbstractModule testModule = new AbstractModule() {
            @Override
            protected void configure() {
                requireBinding(ConfigProvider.class);
                install(new MockZookeeperConnectionModule());
                install(new MidonetBackendTestModule());
                install(new LegacyClusterModule());
                install(new SerializationModule());
            }
        };
        return Guice.createInjector(
            new ConfigProviderModule(config), testModule);
    }

    @Before
    public void before() throws Exception {
        HierarchicalConfiguration config = fillConfig(
            new HierarchicalConfiguration());
        config.setProperty("midolman.midolman_root_key", zkRoot + "/zoom");

        injector = createTestInjector(config);
        injector.getInstance(MidonetBackend.class)
                .startAsync()
                .awaitRunning();

        store = new ZoomStorageService(
            injector.getInstance(MidonetBackend.class).ownershipStore());
        store.registerClasses();
        router = store.create(new Router());
        bridge = store.create(new Bridge());
    }

    @After
    public void after() throws Exception {
        //store.delete(Router.class, router.getId());
        //store.delete(Bridge.class, bridge.getId());
    }

    private Router createRouter(String name) throws StorageException {
        Router rt = new Router();
        rt.setName(name);
        return store.create(rt);
    }

    private Bridge createBridge(String name) throws StorageException {
        Bridge br = new Bridge();
        br.setName(name);
        return store.create(br);
    }

    private RouterPort createRouterPort() throws StorageException {
        RouterPort p = new RouterPort();
        p.setDeviceId(router.getId());
        return store.create(p);
    }

    private BridgePort createBridgePort() throws StorageException {
        BridgePort p = new BridgePort();
        p.setDeviceId(bridge.getId());
        return store.create(p);
    }

    @Test
    public void testCreateRouter() throws Exception {
        Router rt = new Router();
        store.create(rt);
        assertNotNull(rt.getId());
        store.delete(Router.class, rt.getId());
    }

    @Test
    public void testCreateBridge() throws Exception {
        Bridge br = new Bridge();
        store.create(br);
        assertNotNull(br.getId());
        store.delete(Bridge.class, br.getId());
    }

    @Test
    public void testCreateBridgePort() throws Exception {
        BridgePort port = new BridgePort();
        port.setDeviceId(bridge.getId());
        store.create(port);
        assertNotNull(port.getId());
        store.delete(BridgePort.class, port.getId());
    }

    @Test
    public void testCreateRouterPort() throws Exception {
        RouterPort port = new RouterPort();
        port.setDeviceId(router.getId());
        store.create(port);
        assertNotNull(port.getId());
        store.delete(RouterPort.class, port.getId());
    }

    @Test
    public void testGetRouter() throws Exception {
        UUID rtId = createRouter("rt").getId();
        Router rt = store.get(Router.class, rtId);
        assertEquals("rt", rt.getName());
        assertEquals(rtId, rt.getId());
        store.delete(Router.class, rtId);
    }

    @Test
    public void testGetBridge() throws Exception {
        UUID brId = createBridge("br").getId();
        Bridge br = store.get(Bridge.class, brId);
        assertEquals("br", br.getName());
        assertEquals(brId, br.getId());
        store.delete(Bridge.class, brId);
    }

    @Test
    public void testGetRouterPort() throws Exception {
        UUID pId = createRouterPort().getId();
        RouterPort p = store.get(RouterPort.class, pId);
        assertEquals(pId, p.getId());
        store.delete(RouterPort.class, pId);
    }

    @Test
    public void testGetBridgePort() throws Exception {
        UUID pId = createBridgePort().getId();
        BridgePort p = store.get(BridgePort.class, pId);
        assertEquals(pId, p.getId());
        store.delete(BridgePort.class, pId);
    }

    @Test
    public void testGetPort() throws Exception {
        UUID pId = createBridgePort().getId();
        Port p = store.get(Port.class, pId);
        assertEquals(pId, p.getId());
        store.delete(BridgePort.class, pId);
    }

    @Test
    public void testUpdateRouter() throws Exception {
        Router r1 = createRouter("rt");
        r1.setName("rt_name");
        store.update(r1);
        Router r2 = store.get(Router.class, r1.getId());
        assertEquals(r1.getId(), r2.getId());
        assertEquals("rt_name", r2.getName());
        store.delete(Router.class, r1.getId());
    }

    @Test
    public void testUpdateBridge() throws Exception {
        Bridge b1 = createBridge("rt");
        b1.setName("rt_name");
        store.update(b1);
        Bridge b2 = store.get(Bridge.class, b1.getId());
        assertEquals(b1.getId(), b2.getId());
        assertEquals("rt_name", b2.getName());
        store.delete(Bridge.class, b1.getId());
    }

    @Test
    public void testUpdateRouterPort() throws Exception {
        RouterPort p1 = createRouterPort();
        p1.setInterfaceName("rpIf");
        UUID vif_id = UUID.randomUUID();
        p1.setProperty(Port.Property.vif_id, vif_id.toString());
        store.update(p1);
        RouterPort p2 = store.get(RouterPort.class, p1.getId());
        assertEquals(p1.getId(), p2.getId());
        assertEquals(vif_id.toString(), p2.getProperty(Port.Property.vif_id));
        store.delete(RouterPort.class, p1.getId());
    }

    @Test
    public void testUpdateBridgePort() throws Exception {
        Short newVlan = 42;
        BridgePort p1 = createBridgePort();
        UUID vif_id = UUID.randomUUID();
        p1.setProperty(Port.Property.vif_id, vif_id.toString());
        store.update(p1);
        BridgePort p2 = store.get(BridgePort.class, p1.getId());
        assertEquals(p1.getId(), p2.getId());
        assertEquals(vif_id.toString(), p2.getProperty(Port.Property.vif_id));
        store.delete(BridgePort.class, p1.getId());
    }
}
