# NBI

This project implements a REST API for the [tenant manager](https://github.com/AmoVanB/eces-tenant-manager) of the [ECES](https://github.com/AmoVanB/eces-core) framework.

## Usage

The project can be downloaded from maven central using:
```xml
<dependency>
  <groupId>de.tum.ei.lkn.eces</groupId>
  <artifactId>nbi</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

The REST API can be activated simply by instantiating the [NBISystem](src/main/java/de/tum/ei/lkn/eces/nbi/NBISystem.java):

```java
new NBISystem(tenantManagerSystem, controller, NBI_PORT)
```

The API will be exposed on the port `NBI_PORT` and calls will be forwarded to the [TenantManagerSystem](https://github.com/AmoVanB/eces-tenant-manager/blob/master/src/main/java/de/tum/ei/lkn/eces/tenantmanager/TenantManagerSystem.java) passed as parameter.

## API

The system exposes the following API routes:

- `POST`: `/newTenant`.
  - Parameters:
    - `name` (string): name of the tenant.
  - Returns:
    - `id` (int): ID of the created tenant.
    - `cookie` (int): a cookie to reuse when performing actions on behalf of this tenant.
- `POST`: `/newVM`.
  - Parameters:
    - `name` (string): name of the VM.
    - `tenantId` (int): ID of the tenant for which the VM should be created.
    - `cookie` (int): cookie of the tenant.
  - Returns:
    - `id` (int): ID of the VM.
    - `managememnt` (string): a string for connecting to the management interface of the VM. 
- `POST`: `/newFlow`
  - Parameters:
    - `name` (string): flow name.
    - `tenantId` (int): tenant ID.
    - `cookie` (int): cookie of the tenant.
    - `srcIp` (string): source IP of the flow.
    - `dstIp` (string): destination IP of the flow.
    - `srcPort` (int): source port of the flow.
    - `dstPort` (int): destination port of the flow.
    - `protocol` (int): protocol number of the flow.
    - `source` (int): source VM ID.
    - `destination` (int): destination VM ID.
    - `rate` (int): rate of the flow in *bps*.
    - `burst` (int): burt of the flow in *bytes*.
    - `latency` (int): delay requirement of the flow in *ms*.    
  - Returns:
    - `id` (int): ID of the created flow. 
- `POST`: `/removeTenant`
  - Parameters:
    - `tenantId` (int): ID of the tenant to remove.
    - `cookie` (int): cookie of the tenant.
- `POST`: `/removeVM`
  - Parameters:
    - `vmId` (int): ID of the VM to remove.
    - `cookie` (int): cookie of the tenant.
- `POST`: `/removeFlow`
  - Parameters:
    - `flowId` (int): ID of the flow to remove.
    - `cookie` (int): cookie of the tenant.

These routes correspond to the identically named functions of the [TenantManagerSystem](https://github.com/AmoVanB/eces-tenant-manager/blob/master/src/main/java/de/tum/ei/lkn/eces/tenantmanager/TenantManagerSystem.java).

See [tests](src/test) for examples of how to (and to not) use the interface.