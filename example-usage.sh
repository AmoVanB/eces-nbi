#!/bin/bash

# Example script that uses the NBI to create a tenant,
# two VMs, and a bidirectional flow between these two
# VMs.
#
# Author: Amaury Van Bemten <amaury.van-bemten@tum.de>

CONTROLLER=10.152.4.106
PORT=22222
ROOT_PATH=/

random_string() {
	date | md5sum | cut -d " " -f 1 | cut -c1-5
}

ssh_no_key_with_port() {
    ssh -o StrictHostKeyChecking=no -o LogLevel=QUIET -p $@
}

scp_no_key_with_port() {
    scp -q -o StrictHostKeyChecking=no -o LogLevel=QUIET -P $@
}

rest_call() {
	function=$1
	data=$2
	curl --fail --silent --show-error -H "Content-type: application/json" -X POST http://${CONTROLLER}:${PORT}${ROOT_PATH}${function} -d "${data}"
}

create_tenant() {
	JSON_ARRAY=$(rest_call "newTenant" "{\"name\": \"$1\"}")
	echo $JSON_ARRAY | jq -r '.id'
	echo $JSON_ARRAY | jq -r '.cookie'
}

create_vm() {
	JSON_ARRAY=$(rest_call "newVM" "{\"tenantId\": $1, \"cookie\": $2, \"name\":  \"$3\"}")
	echo $JSON_ARRAY | jq -r '.id'
	echo $JSON_ARRAY | jq -r '.management'
}

create_flow() {
	JSON_ARRAY=$(rest_call "newFlow" "{\"tenantId\": $1, \"cookie\": $2, \"name\":  \"$3\", \"source\": $4, \"destination\": $5, \"srcIp\": \"$6\", \"dstIp\": \"$7\", \"srcPort\": $8, \"dstPort\": $9, \"protocol\": ${10}, \"rate\": ${11}, \"burst\": ${12}, \"latency\": ${13}}")
	echo $JSON_ARRAY
}

echo "Creating tenant"
TENANT_DATA=$(create_tenant "tenant #$(random_string)")
tenant_id=$(echo $TENANT_DATA | cut -d " " -f 1)
cookie_id=$(echo $TENANT_DATA | cut -d " " -f 2)
echo "tenant_id=$tenant_id cookie_id=$cookie_id"

echo "Creating VM"
VM_DATA=$(create_vm $tenant_id $cookie_id "VM #$(random_string)")
vm_1_id=$(echo $VM_DATA | cut -d " " -f 1)
vm_1_mgmt=$(echo $VM_DATA | cut -d " " -f 2- | cut -d "p" -f 2-)
echo "vm_id=$vm_id vm_mgmt=$vm_mgmt"

echo "Creating VM"
VM_DATA=$(create_vm $tenant_id $cookie_id "VM #$(random_string)")
vm_2_id=$(echo $VM_DATA | cut -d " " -f 1)
vm_2_mgmt=$(echo $VM_DATA | cut -d " " -f 2- | cut -d "p" -f 2-)
echo "vm_id=$vm_id vm_mgmt=$vm_mgmt"

echo "Creating bidirectional flow"
create_flow $tenant_id $cookie_id "flow #$(random_string)" $vm_1_id $vm_2_id "30.0.0.1" "30.0.0.2" 5000 6000 6 250000 2500 10
create_flow $tenant_id $cookie_id "flow #$(random_string)" $vm_2_id $vm_1_id "30.0.0.2" "30.0.0.1" 6000 5000 6 250000000 2500 10
