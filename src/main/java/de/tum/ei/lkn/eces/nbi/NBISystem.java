package de.tum.ei.lkn.eces.nbi;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.RootSystem;
import de.tum.ei.lkn.eces.nbi.mappers.CookieMapper;
import de.tum.ei.lkn.eces.tenantmanager.Flow;
import de.tum.ei.lkn.eces.tenantmanager.Tenant;
import de.tum.ei.lkn.eces.tenantmanager.TenantManagerSystem;
import de.tum.ei.lkn.eces.tenantmanager.VirtualMachine;
import de.tum.ei.lkn.eces.tenantmanager.exceptions.TenantManagerException;
import org.json.JSONException;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Northbound interface (SBI) system responsible for exposing an interface to applications.
 *
 * The system allows applications to request flow embeddings through a REST API.
 *
 * @author Amaury Van Bemten
 */
public class NBISystem extends RootSystem {
    private Map<Long, Tenant> tenants;
    private Map<Long, VirtualMachine> vms;
    private Map<Long, Flow> flows;

    private TenantManagerSystem tenantManagerSystem;

    private CookieMapper cookieMapper;

    public NBISystem(TenantManagerSystem tenantManagerSystem, Controller controller) {
        this(tenantManagerSystem, controller, 8091);
    }

    public NBISystem(TenantManagerSystem tenantManagerSystem, Controller controller, int port) {
        super(controller);

        tenants = new HashMap<>();
        vms = new HashMap<>();
        flows = new HashMap<>();

        this.tenantManagerSystem = tenantManagerSystem;

        cookieMapper = new CookieMapper(controller);

        Spark.port(port);
        Spark.post("/newTenant", this::newTenant);
        Spark.post("/newVM", this::newVM);
        Spark.post("/newFlow", this::newFlow);
        Spark.post("/removeTenant", this::removeTenant);
        Spark.post("/removeVM", this::removeVM);
        Spark.post("/removeFlow", this::removeFlow);
        Spark.awaitInitialization();
    }

    public void stop() {
        Spark.stop();
        Spark.awaitStop();
    }

    private JSONObject getJONObject(String string) {
        JSONObject json;
        try {
            json = new JSONObject(string);
        }
        catch(JSONException e) {
            return null;
        }

        return json;
    }

    private Object newTenant(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object nameObj;
        try {
            nameObj = json.get("name");
        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(nameObj instanceof String)) {
            String message = "Name should be a string";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        String name = (String) nameObj;

        if(name.equals("")) {
            String message = "Name should not be empty";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        if(name.length() > 15) {
            String message = "Name should be at most 15 characters";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Everything is fine, let's create him
        Tenant tenant;
        try {
            tenant = tenantManagerSystem.createTenant(name);
        } catch (TenantManagerException e) {
            logger.error(e.getMessage());
            return Spark.halt(400, e.getMessage());
        }

        Cookie cookie = new Cookie(Math.abs(new Random().nextInt()));
        cookieMapper.attachComponent(tenant.getEntity(), cookie);
        tenants.put(tenant.getId(), tenant);
        response.type("application/json");
        return "{\"id\": " + tenant.getId() + ", \"cookie\": " + cookie.getCookie() + "}";
    }

    private Object newVM(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object nameObj, tenantIdObj, cookieObj;
        try {
            nameObj = json.get("name");
            tenantIdObj = json.get("tenantId");
            cookieObj = json.get("cookie");

        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(nameObj instanceof String)) {
            String message = "Name should be a string";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        String name = (String) nameObj;

        if(!(tenantIdObj instanceof Integer) & !(tenantIdObj instanceof Long)) {
            String message = "Tenant ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long tenantId = 0;
        if(tenantIdObj instanceof Long)
            tenantId = (long) tenantIdObj;
        if(tenantIdObj instanceof Integer)
            tenantId = ((Integer) tenantIdObj).longValue();

        if(!(cookieObj instanceof Integer)) {
            String message = "Cookie should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int cookie = (int) cookieObj;

        // Checking tenant exists
        if(!tenants.containsKey(tenantId)) {
            String message = "Invalid tenant ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking cookie
        if(cookieMapper.get(tenants.get(tenantId).getEntity()).getCookie() != cookie) {
            String message = "Invalid cookie";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        VirtualMachine vm;
        try {
            vm = tenantManagerSystem.createVirtualMachine(tenants.get(tenantId), name);
        } catch (TenantManagerException e) {
            logger.error(e.getMessage());
            return Spark.halt(400, e.getMessage());
        }

        vms.put(vm.getId(), vm);
        response.type("application/json");
        return "{\"id\": " + vm.getId() + ", \"management\": \"" + ((vm.getManagementConnection() == null) ? "unknown" : vm.getManagementConnection()) + "\"}";
    }

    private Object newFlow(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object nameObj, tenantIdObj, cookieObj, srcIpObj, dstIpObj, srcPortObj, dstPortObj, protocolObj, srcObj, dstObj, rateObj, burstObj, latencyObj;
        try {
            nameObj = json.get("name");
            tenantIdObj = json.get("tenantId");
            cookieObj = json.get("cookie");
            srcIpObj = json.get("srcIp");
            dstIpObj = json.get("dstIp");
            srcPortObj = json.get("srcPort");
            dstPortObj = json.get("dstPort");
            protocolObj = json.get("protocol");
            srcObj = json.get("source");
            dstObj = json.get("destination");
            rateObj = json.get("rate"); // bps
            burstObj = json.get("burst"); // bytes
            latencyObj = json.get("latency"); // ms
        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(nameObj instanceof String)) {
            String message = "Name should be a string";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        String name = (String) nameObj;

        if(!(tenantIdObj instanceof Integer) & !(tenantIdObj instanceof Long)) {
            String message = "Tenant ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long tenantId = 0;
        if(tenantIdObj instanceof Long)
            tenantId = (long) tenantIdObj;
        if(tenantIdObj instanceof Integer)
            tenantId = ((Integer) tenantIdObj).longValue();

        if(!(cookieObj instanceof Integer)) {
            String message = "Cookie should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int cookie = (int) cookieObj;

        if(!(srcIpObj instanceof String)) {
            String message = "Source IP should be a string representing an IP";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        InetAddress srcIp;
        try {
            srcIp = Inet4Address.getByName((String) srcIpObj);
        } catch (UnknownHostException e) {
            String message = "Source IP is not a valid IPv4 address";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        if(!(dstIpObj instanceof String)) {
            String message = "Destination IP should be a string representing an IP";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        InetAddress dstIp;
        try {
            dstIp = Inet4Address.getByName((String) dstIpObj);
        } catch (UnknownHostException e) {
            String message = "Destination IP is not a valid IPv4 address";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        if(!(srcPortObj instanceof Integer)) {
            String message = "Source port should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int srcPort = (int) srcPortObj;

        if(!(dstPortObj instanceof Integer)) {
            String message = "Destination port should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int dstPort = (int) dstPortObj;

        if(!(protocolObj instanceof Integer)) {
            String message = "Protocol number should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int protocol = (int) protocolObj;

        // Checking tenant exists
        if(!tenants.containsKey(tenantId)) {
            String message = "Invalid tenant ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking cookie
        if(cookieMapper.get(tenants.get(tenantId).getEntity()).getCookie() != cookie) {
            String message = "Invalid cookie";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking source
        if(!(srcObj instanceof Integer) && !(srcObj instanceof Long)) {
            String message = "Source VM ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long src = 0;
        if(srcObj instanceof Long)
            src = (long) srcObj;
        if(srcObj instanceof Integer)
            src = ((Integer) srcObj).longValue();

        // Checking VM exists
        if(!vms.containsKey(src) || vms.get(src).getTenant().getId() != tenantId) {
            String message = "Invalid source VM ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking destination
        if(!(dstObj instanceof Integer) && !(dstObj instanceof Long)) {
            String message = "Destination VM ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long dst = 0;
        if(dstObj instanceof Long)
            dst = (long) dstObj;
        if(dstObj instanceof Integer)
            dst = ((Integer) dstObj).longValue();

        // Checking VM exists and belongs to tenant
        if(!vms.containsKey(dst) || vms.get(dst).getTenant().getId() != tenantId) {
            String message = "Invalid destination VM ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking rate
        if(!(rateObj instanceof Integer) & !(rateObj instanceof Long)) {
            String message = "Rate should be a number";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long rate = 0;
        if(burstObj instanceof Long)
            rate = (long) rateObj;
        if(burstObj instanceof Integer)
            rate = ((Integer) rateObj).longValue();

        // Checking burst
        if(!(burstObj instanceof Integer) & !(burstObj instanceof Long)) {
            String message = "Burst should be an int/long";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long burst = 0;
        if(burstObj instanceof Long)
            burst = (long) burstObj;
        if(burstObj instanceof Integer)
            burst = ((Integer) burstObj).longValue();

        // Checking latency
        if(!(latencyObj instanceof Number)) {
            String message = "Rate should be a number";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        double latency = ((Number) latencyObj).doubleValue();

        Flow flow;
        try {
            flow = tenantManagerSystem.createFlow(name, vms.get(src), vms.get(dst), srcIp, dstIp, srcPort, dstPort, protocol, rate, burst, latency);
        } catch (TenantManagerException e) {
            logger.error(e.getMessage());
            return Spark.halt(400, e.getMessage());
        }

        flows.put(flow.getId(), flow);
        response.type("application/json");
        return "{\"id\": " + flow.getId() + "}";
    }

    private Object removeFlow(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object flowIdObj, cookieObj;
        try {
            flowIdObj = json.get("flowId");
            cookieObj = json.get("cookie");
        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(flowIdObj instanceof Integer) && !(flowIdObj instanceof Long)) {
            String message = "Flow ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long flowId = 0;
        if(flowIdObj instanceof Long)
            flowId = (long) flowIdObj;
        if(flowIdObj instanceof Integer)
            flowId = ((Integer) flowIdObj).longValue();

        if(!(cookieObj instanceof Integer)) {
            String message = "Cookie should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int cookie = (int) cookieObj;

        // Checking flow exists
        if(!flows.containsKey(flowId)) {
            String message = "Invalid flow ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking cookie
        if(cookieMapper.get(flows.get(flowId).getSource().getTenant().getEntity()).getCookie() != cookie) {
            String message = "Invalid cookie";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        tenantManagerSystem.deleteFlow(flows.get(flowId));
        flows.remove(flowId);
        response.type("application/json");
        return "{\"success\": 1}";
    }

    private Object removeVM(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object vmIdObj, cookieObj;
        try {
            vmIdObj = json.get("vmId");
            cookieObj = json.get("cookie");
        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(vmIdObj instanceof Integer) && !(vmIdObj instanceof Long)) {
            String message = "VM ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long vmId = 0;
        if(vmIdObj instanceof Long)
            vmId = (long) vmIdObj;
        if(vmIdObj instanceof Integer)
            vmId = ((Integer) vmIdObj).longValue();

        if(!(cookieObj instanceof Integer)) {
            String message = "Cookie should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int cookie = (int) cookieObj;

        // Checking VM exists
        if(!vms.containsKey(vmId)) {
            String message = "Invalid VM ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking cookie
        if(cookieMapper.get(vms.get(vmId).getTenant().getEntity()).getCookie() != cookie) {
            String message = "Invalid cookie";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        tenantManagerSystem.deleteVM(vms.get(vmId));
        vms.remove(vmId);
        response.type("application/json");
        return "{\"success\": 1}";
    }

    private Object removeTenant(Request request, Response response) {
        String preErrorLogMessage = request.pathInfo() + ": " + request.body().replace("\n", " ") + ": ";

        logger.info(preErrorLogMessage);

        JSONObject json = getJONObject(request.body());
        if(json == null) {
            logger.error(request.body() + " is not a valid JSON format!");
            return Spark.halt(400, "This is not a valid JSON format");
        }

        Object tenantIdObj, cookieObj;
        try {
            tenantIdObj = json.get("tenantId");
            cookieObj = json.get("cookie");
        }
        catch(JSONException e) {
            String message = "A key is missing: " + e;
            logger.error(message);
            return Spark.halt(400, message);
        }

        if(!(tenantIdObj instanceof Integer) & !(tenantIdObj instanceof Long)) {
            String message = "Tenant ID should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        long tenantId = 0;
        if(tenantIdObj instanceof Long)
            tenantId = (long) tenantIdObj;
        if(tenantIdObj instanceof Integer)
            tenantId = ((Integer) tenantIdObj).longValue();

        if(!(cookieObj instanceof Integer)) {
            String message = "Cookie should be an integer";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }
        int cookie = (int) cookieObj;

        // Checking VM exists
        if(!tenants.containsKey(tenantId)) {
            String message = "Invalid tenant ID";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        // Checking cookie
        if(cookieMapper.get(tenants.get(tenantId).getEntity()).getCookie() != cookie) {
            String message = "Invalid cookie";
            logger.error(preErrorLogMessage + message);
            return Spark.halt(400, message);
        }

        tenantManagerSystem.deleteTenant(tenants.get(tenantId));
        tenants.remove(tenantId);
        response.type("application/json");
        return "{\"success\": 1}";
    }
}
