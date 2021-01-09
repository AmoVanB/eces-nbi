package de.tum.ei.lkn.eces.nbi;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.DNMSystem;
import de.tum.ei.lkn.eces.dnm.ResidualMode;
import de.tum.ei.lkn.eces.dnm.config.ACModel;
import de.tum.ei.lkn.eces.dnm.config.BurstIncreaseModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.Division;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.LowerLimit;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.Summation;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.UpperLimit;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.Constant;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.QueuePriority;
import de.tum.ei.lkn.eces.dnm.mappers.DetServConfigMapper;
import de.tum.ei.lkn.eces.dnm.proxies.DetServProxy;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM.MHMRateRatiosAllocation;
import de.tum.ei.lkn.eces.graph.GraphSystem;
import de.tum.ei.lkn.eces.network.Host;
import de.tum.ei.lkn.eces.network.Network;
import de.tum.ei.lkn.eces.network.NetworkNode;
import de.tum.ei.lkn.eces.network.NetworkingSystem;
import de.tum.ei.lkn.eces.network.util.NetworkInterface;
import de.tum.ei.lkn.eces.routing.RoutingSystem;
import de.tum.ei.lkn.eces.routing.algorithms.RoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.csp.unicast.cbf.CBFAlgorithm;
import de.tum.ei.lkn.eces.routing.pathlist.PathListSystem;
import de.tum.ei.lkn.eces.tenantmanager.TenantManagerSystem;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class NBISystemTest {
    private static int NBI_PORT = 8092;
    private static String NEW_TENANT_PATH = "/newTenant";
    private static String NEW_VM_PATH = "/newVM";
    private static String NEW_FLOW_PATH = "/newFlow";
    private static String REMOVE_TENANT_PATH = "/removeTenant";
    private static String REMOVE_VM_PATH = "/removeVM";
    private static String REMOVE_FLOW_PATH = "/removeFlow";

    private NBISystem nbiSystem;

    @Before
    public void setUp() {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
        Logger.getLogger("de.tum.ei.lkn.eces.dnm").setLevel(Level.ERROR);
        Logger.getLogger("de.tum.ei.lkn.eces.graph").setLevel(Level.ERROR);
        Logger.getLogger("de.tum.ei.lkn.eces.network").setLevel(Level.ERROR);
        Logger.getLogger("org.eclipse.jetty").setLevel(Level.ERROR);

        Controller controller = new Controller();
        GraphSystem graphSystem = new GraphSystem(controller);
        NetworkingSystem networkingSystem = new NetworkingSystem(controller, graphSystem);

        DetServConfig modelConfig = new DetServConfig(
                ACModel.MHM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        1), 0),
                (controller1, scheduler) -> new MHMRateRatiosAllocation(controller1, new double[]{1.0 / 4, 1.0 / 5, 1.0 / 6, 1.0 / 8}));


        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm cbf = new CBFAlgorithm(controller);
        cbf.setProxy(proxy);
        modelConfig.initCostModel(controller);

        // Path list system
        PathListSystem pathListSystem = new PathListSystem(controller);

        // Create network
        Network network = networkingSystem.createNetwork();
        DetServConfigMapper modelingConfigMapper = new DetServConfigMapper(controller);
        modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

        Host lundi = networkingSystem.createHost(network, "lundi");
        Host mardi = networkingSystem.createHost(network, "mardi");
        NetworkNode lundiNode = networkingSystem.addInterface(lundi, new NetworkInterface("1", "00:00:00:00:00:00"));
        NetworkNode mardiNode = networkingSystem.addInterface(mardi, new NetworkInterface("2", "00:00:00:00:00:00"));

        NetworkNode node = networkingSystem.createNode(network);
        networkingSystem.createLinkWithPriorityScheduling(lundiNode, node, 1e9 / 8, 0, new double[]{30000});
        networkingSystem.createLinkWithPriorityScheduling(node, lundiNode, 1e9 / 8, 0, new double[]{30000});
        networkingSystem.createLinkWithPriorityScheduling(mardiNode, node, 1e9 / 8, 0, new double[]{30000});
        networkingSystem.createLinkWithPriorityScheduling(node, mardiNode, 1e9 / 8, 0, new double[]{30000});

        nbiSystem = new NBISystem(new TenantManagerSystem(network, cbf, controller), controller, NBI_PORT);
    }

    @After
    public void cleanUp() {
        nbiSystem.stop();
    }

    @Test
    public void checkWrongPaths() throws IOException {
        assertEquals(sendPost("/wrong", "shit").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/another", "shit").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/okay", "shit").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/newUser", "shit").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/", "shit").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/another", "thischanges").getStatusLine().getStatusCode(), 404);
        assertEquals(sendPost("/wrong", "").getStatusLine().getStatusCode(), 404);
    }

    @Test
    public void testNewTenant() throws IOException {
        // Invalid JSON
        assertEquals(sendPost(NEW_TENANT_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (name)
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"ok\": fine}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"id\": 4}").getStatusLine().getStatusCode(), 400);
        // Key is there but type of value is wrong
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": 4.3}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": -4}").getStatusLine().getStatusCode(), 400);
        // Now should be fine
        HttpResponse response = sendPost(NEW_TENANT_PATH, "{\"name\": ok}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && json.get("cookie") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long) && json.get("cookie") instanceof Integer);
        response = sendPost(NEW_TENANT_PATH, "{\"name\": \"another name\"}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && json.get("cookie") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long) && json.get("cookie") instanceof Integer);
        response = sendPost(NEW_TENANT_PATH, "{\"name\": \"another2 name\"}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && json.get("cookie") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long) && json.get("cookie") instanceof Integer);
        // Now same names, should fail
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": \"another2 name\"}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": \"another name\"}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": ok}").getStatusLine().getStatusCode(), 400);
        // Empty name should fail
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": \"\"}").getStatusLine().getStatusCode(), 400);
        // Too long name should fail
        assertEquals(sendPost(NEW_TENANT_PATH, "{\"name\": \"I am a very long name my friend!\"}").getStatusLine().getStatusCode(), 400);
    }

    @Test
    public void testNewVM() throws IOException {
        // Create two tenants
        HttpResponse tenant1Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant1}");
        HttpResponse tenant2Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant2}");
        assertEquals(tenant1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(tenant2Response.getStatusLine().getStatusCode(), 200);
        JSONObject tenant1Json = getResponseContent(tenant1Response);
        JSONObject tenant2Json = getResponseContent(tenant2Response);
        long tenant1Id = tenant1Json.getLong("id");
        long tenant2Id = tenant2Json.getLong("id");
        int tenant1Cookie = tenant1Json.getInt("cookie");
        int tenant2Cookie = tenant2Json.getInt("cookie");

        // Invalid JSON
        assertEquals(sendPost(NEW_VM_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (name, tenantId, cookie)
        assertEquals(sendPost(NEW_VM_PATH, "{\"ok\": fine}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"id\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        // Key is there but type of value is wrong
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": 1, \"tenantId\": 4, \"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": ok, \"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": 4, \"cookie\": lol}").getStatusLine().getStatusCode(), 400);
        // Wrong ID
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id + tenant2Id) + ", \"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        // Wrong cookie
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant2Cookie + "}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Correct cookie
        HttpResponse response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long) && json.get("management") != null && json.get("management") instanceof String);
        response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long) && json.get("management") != null && json.get("management") instanceof String);
        // Should fail because same name
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}").getStatusLine().getStatusCode(), 400);
    }

    @Test
    public void testNewFlow() throws IOException {
        // Create two tenants
        HttpResponse tenant1Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant1}");
        HttpResponse tenant2Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant2}");
        assertEquals(tenant1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(tenant2Response.getStatusLine().getStatusCode(), 200);
        JSONObject tenant1Json = getResponseContent(tenant1Response);
        JSONObject tenant2Json = getResponseContent(tenant2Response);
        long tenant1Id = tenant1Json.getLong("id");
        long tenant2Id = tenant2Json.getLong("id");
        int tenant1Cookie = tenant1Json.getInt("cookie");
        int tenant2Cookie = tenant2Json.getInt("cookie");

        // Create 3 VMs
        HttpResponse vm1Response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}");
        HttpResponse vm2Response = sendPost(NEW_VM_PATH, "{\"name\": vm1, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        HttpResponse vm3Response = sendPost(NEW_VM_PATH, "{\"name\": vm2, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        assertEquals(vm1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm3Response.getStatusLine().getStatusCode(), 200);
        JSONObject vm1Json = getResponseContent(vm1Response);
        JSONObject vm2Json = getResponseContent(vm2Response);
        JSONObject vm3Json = getResponseContent(vm3Response);
        long vm1Id = vm1Json.getLong("id");
        long vm2Id = vm2Json.getLong("id");
        long vm3Id = vm3Json.getLong("id");

        // Invalid JSON
        assertEquals(sendPost(NEW_FLOW_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (name, tenantId, cookie, srcIp, dstIp, srcPort, dstPort, protocol, src, dst, rate, burst, latency)
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine, \"tenantId\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine, \"tenantId\": 4, \"cookie\": 5}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": 4," +
                "\"cookie\": 5," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 5," +
                "\"dstPort\": 5," +
                "\"protocol\": 5," +
                "\"source\": 5," +
                "\"destination\": 4," +
                "\"rate\": 25," +
                "\"burst\": 5}").getStatusLine().getStatusCode(), 400); // missing latency
        // Key is there but type of value is wrong
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 5," +
                "\"dstPort\": 5," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong IP
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.a," +
                "\"srcPort\": 5," +
                "\"dstPort\": 5," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong IP
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": okay," +
                "\"dstPort\": 5," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong port
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": lol," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong port
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5.2," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong protocol
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 0," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong rate
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": test," +
                "\"burst\": 5," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong rate
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 0," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong burst
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": text," +
                "\"latency\": 5}").getStatusLine().getStatusCode(), 400); // wrong burst
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 12," +
                "\"latency\": 0}").getStatusLine().getStatusCode(), 400); // wrong latency
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": latency}").getStatusLine().getStatusCode(), 400); // wrong latency
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": lol," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400); // wrong VM ID
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": lol," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400); // wrong VM ID
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": ok," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400); // wrong tenant ID
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": test," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400); // wrong cookie ID
        // Wrong tenant ID
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + (tenant1Id + tenant2Id) + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        // Wrong VM ID
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm1Id + "," +
                "\"destination\": " + (vm1Id + vm2Id) + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + (vm1Id + vm2Id) + "," +
                "\"destination\": " + vm1Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        // Wrong cookie
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant1Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        // correct
        HttpResponse response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long));
        // VMs from different tenants
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm1Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        // Same matching as existing VM
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
        // Reverse should be fine though since source VM is different
        response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long));
        // Let's also make sure a different matching is fine
        response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 44," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("id") != null && (json.get("id") instanceof Integer || json.get("id") instanceof Long));
        // The VMs do not belong to the tenant
        assertEquals(sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant1Id + "," +
                "\"cookie\": " + tenant1Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 25," +
                "\"dstPort\": 25," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}").getStatusLine().getStatusCode(), 400);
    }

    @Test
    public void testDeleteFlow() throws IOException {
        // Create two tenants
        HttpResponse tenant1Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant1}");
        HttpResponse tenant2Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant2}");
        assertEquals(tenant1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(tenant2Response.getStatusLine().getStatusCode(), 200);
        JSONObject tenant1Json = getResponseContent(tenant1Response);
        JSONObject tenant2Json = getResponseContent(tenant2Response);
        long tenant1Id = tenant1Json.getLong("id");
        long tenant2Id = tenant2Json.getLong("id");
        int tenant1Cookie = tenant1Json.getInt("cookie");
        int tenant2Cookie = tenant2Json.getInt("cookie");

        // Create 3 VMs
        HttpResponse vm1Response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}");
        HttpResponse vm2Response = sendPost(NEW_VM_PATH, "{\"name\": vm1, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        HttpResponse vm3Response = sendPost(NEW_VM_PATH, "{\"name\": vm2, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        assertEquals(vm1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm3Response.getStatusLine().getStatusCode(), 200);
        JSONObject vm1Json = getResponseContent(vm1Response);
        JSONObject vm2Json = getResponseContent(vm2Response);
        JSONObject vm3Json = getResponseContent(vm3Response);
        long vm1Id = vm1Json.getLong("id");
        long vm2Id = vm2Json.getLong("id");
        long vm3Id = vm3Json.getLong("id");

        // Add 3 flows
        HttpResponse flow1Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow1Json = getResponseContent(flow1Response);
        HttpResponse flow2Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow2Json = getResponseContent(flow2Response);
        HttpResponse flow3Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 44," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow3Json = getResponseContent(flow3Response);
        assertEquals(flow1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow3Response.getStatusLine().getStatusCode(), 200);
        long flow1Id = flow1Json.getLong("id");
        long flow2Id = flow2Json.getLong("id");
        long flow3Id = flow3Json.getLong("id");

        // Invalid JSON
        assertEquals(sendPost(REMOVE_FLOW_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (flowId, cookie)
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        // Key is there but type of value is wrong
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": 4, \"cookie\": lol}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": la, \"cookie\": 43}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": 4, \"cookie\": 43.3}").getStatusLine().getStatusCode(), 400);
        // Invalid flow ID
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": " + (flow1Id + flow2Id) + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Invalid cookie (wrong tenant)
        assertEquals(sendPost(REMOVE_FLOW_PATH, "{\"flowId\": " + flow3Id + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Should work to delete all
        HttpResponse response = sendPost(REMOVE_FLOW_PATH, "{\"flowId\": " + flow1Id + ", \"cookie\": " + tenant2Cookie + "}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
        response = sendPost(REMOVE_FLOW_PATH, "{\"flowId\": " + flow2Id + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
        response = sendPost(REMOVE_FLOW_PATH, "{\"flowId\": " + flow3Id + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
    }

    @Test
    public void testDeleteVM() throws IOException {
        // Create two tenants
        HttpResponse tenant1Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant1}");
        HttpResponse tenant2Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant2}");
        assertEquals(tenant1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(tenant2Response.getStatusLine().getStatusCode(), 200);
        JSONObject tenant1Json = getResponseContent(tenant1Response);
        JSONObject tenant2Json = getResponseContent(tenant2Response);
        long tenant1Id = tenant1Json.getLong("id");
        long tenant2Id = tenant2Json.getLong("id");
        int tenant1Cookie = tenant1Json.getInt("cookie");
        int tenant2Cookie = tenant2Json.getInt("cookie");

        // Create 3 VMs
        HttpResponse vm1Response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}");
        HttpResponse vm2Response = sendPost(NEW_VM_PATH, "{\"name\": vm1, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        HttpResponse vm3Response = sendPost(NEW_VM_PATH, "{\"name\": vm2, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        assertEquals(vm1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm3Response.getStatusLine().getStatusCode(), 200);
        JSONObject vm1Json = getResponseContent(vm1Response);
        JSONObject vm2Json = getResponseContent(vm2Response);
        JSONObject vm3Json = getResponseContent(vm3Response);
        long vm1Id = vm1Json.getLong("id");
        long vm2Id = vm2Json.getLong("id");
        long vm3Id = vm3Json.getLong("id");

        // Add 3 flows
        HttpResponse flow1Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow1Json = getResponseContent(flow1Response);
        HttpResponse flow2Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow2Json = getResponseContent(flow2Response);
        HttpResponse flow3Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 44," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow3Json = getResponseContent(flow3Response);
        assertEquals(flow1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow3Response.getStatusLine().getStatusCode(), 200);
        long flow1Id = flow1Json.getLong("id");
        long flow2Id = flow2Json.getLong("id");
        long flow3Id = flow3Json.getLong("id");

        // Invalid JSON
        assertEquals(sendPost(REMOVE_VM_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (vmId, cookie)
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        // Key is there but type of value is wrong
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": 4, \"cookie\": lol}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": la, \"cookie\": 43}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": 4, \"cookie\": 43.3}").getStatusLine().getStatusCode(), 400);
        // Invalid VM ID
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": " + (vm2Id + vm3Id) + ", \"cookie\": " + tenant2Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Invalid cookie (wrong tenant)
        assertEquals(sendPost(REMOVE_VM_PATH, "{\"vmId\": " + vm3Id + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Should work to delete all
        HttpResponse response = sendPost(REMOVE_VM_PATH, "{\"vmId\": " + vm1Id + ", \"cookie\": " + tenant1Cookie + "}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
        response = sendPost(REMOVE_VM_PATH, "{\"vmId\": " + vm2Id + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
        response = sendPost(REMOVE_VM_PATH, "{\"vmId\": " + vm3Id + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
    }

    @Test
    public void testDeleteTenant() throws IOException {
        // Create two tenants
        HttpResponse tenant1Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant1}");
        HttpResponse tenant2Response = sendPost(NEW_TENANT_PATH, "{\"name\": tenant2}");
        assertEquals(tenant1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(tenant2Response.getStatusLine().getStatusCode(), 200);
        JSONObject tenant1Json = getResponseContent(tenant1Response);
        JSONObject tenant2Json = getResponseContent(tenant2Response);
        long tenant1Id = tenant1Json.getLong("id");
        long tenant2Id = tenant2Json.getLong("id");
        int tenant1Cookie = tenant1Json.getInt("cookie");
        int tenant2Cookie = tenant2Json.getInt("cookie");

        // Create 3 VMs
        HttpResponse vm1Response = sendPost(NEW_VM_PATH, "{\"name\": ok, \"tenantId\": " + (tenant1Id) + ", \"cookie\": " + tenant1Cookie + "}");
        HttpResponse vm2Response = sendPost(NEW_VM_PATH, "{\"name\": vm1, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        HttpResponse vm3Response = sendPost(NEW_VM_PATH, "{\"name\": vm2, \"tenantId\": " + (tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}");
        assertEquals(vm1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(vm3Response.getStatusLine().getStatusCode(), 200);
        JSONObject vm1Json = getResponseContent(vm1Response);
        JSONObject vm2Json = getResponseContent(vm2Response);
        JSONObject vm3Json = getResponseContent(vm3Response);
        long vm1Id = vm1Json.getLong("id");
        long vm2Id = vm2Json.getLong("id");
        long vm3Id = vm3Json.getLong("id");

        // Add 3 flows
        HttpResponse flow1Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow1Json = getResponseContent(flow1Response);
        HttpResponse flow2Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 41," +
                "\"protocol\": 5," +
                "\"source\": " + vm2Id + "," +
                "\"destination\": " + vm3Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow2Json = getResponseContent(flow2Response);
        HttpResponse flow3Response = sendPost(NEW_FLOW_PATH, "{\"name\": fine," +
                "\"tenantId\": " + tenant2Id + "," +
                "\"cookie\": " + tenant2Cookie + "," +
                "\"srcIp\": 10.152.1.1," +
                "\"dstIp\": 10.152.1.2," +
                "\"srcPort\": 4," +
                "\"dstPort\": 44," +
                "\"protocol\": 5," +
                "\"source\": " + vm3Id + "," +
                "\"destination\": " + vm2Id + "," +
                "\"rate\": 25," +
                "\"burst\": 5," +
                "\"latency\": 22}");
        JSONObject flow3Json = getResponseContent(flow3Response);
        assertEquals(flow1Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow2Response.getStatusLine().getStatusCode(), 200);
        assertEquals(flow3Response.getStatusLine().getStatusCode(), 200);
        long flow1Id = flow1Json.getLong("id");
        long flow2Id = flow2Json.getLong("id");
        long flow3Id = flow3Json.getLong("id");

        // Invalid JSON
        assertEquals(sendPost(REMOVE_TENANT_PATH, "shit").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"}").getStatusLine().getStatusCode(), 400);
        // Missing necessary key (tenantId, cookie)
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": 4}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"cookie\": 4}").getStatusLine().getStatusCode(), 400);
        // Key is there but type of value is wrong
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": 4, \"cookie\": lol}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": la, \"cookie\": 43}").getStatusLine().getStatusCode(), 400);
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": 4, \"cookie\": 43.3}").getStatusLine().getStatusCode(), 400);
        // Invalid tenantId ID
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": " + (tenant1Id + tenant2Id) + ", \"cookie\": " + tenant2Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Invalid cookie (wrong tenant)
        assertEquals(sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": " + tenant2Id + ", \"cookie\": " + tenant1Cookie + "}").getStatusLine().getStatusCode(), 400);
        // Should work to delete all
        HttpResponse response = sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": " + tenant1Id + ", \"cookie\": " + tenant1Cookie + "}");
        JSONObject json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
        response = sendPost(REMOVE_TENANT_PATH, "{\"tenantId\": " + tenant2Id + ", \"cookie\": " + tenant2Cookie + "}");
        json = getResponseContent(response);
        assertEquals(response.getStatusLine().getStatusCode(), 200);
        assertTrue(json != null && json.get("success") != null && json.get("success").equals(1));
    }

    // Helper to send a POST request
    private HttpResponse sendPost(String path, String jsonString) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:" + NBI_PORT + "/" + path);
        StringEntity input = new StringEntity(jsonString);
        post.setEntity(input);
        return client.execute(post);
    }

    // Helper to get the JSON Object of a POST request
    private JSONObject getResponseContent(HttpResponse response) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            stringBuilder.append(line);
        }

        try {
            return new JSONObject(stringBuilder.toString());
        }
        catch (JSONException e) {
            // Not a valid JSON, this should never happen
            fail(stringBuilder.toString());
            return null;
        }
    }
}

