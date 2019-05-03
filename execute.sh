#!/bin/bash

$JBOSS_HOME/bin/add-user.sh -up mgmt-users.properties $adminuser $adminpass --silent

echo "=> Stating WildFly"

$JBOSS_HOME/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 --debug
