#!/bin/bash

# Run dnf installs on first container run
if [ ! -f /var/lib/ats_service_initialized ]; then
    dnf -y update --refresh
    dnf -y install jq git gh wget curl fish java-25-openjdk-devel nodejs npm make
    touch /var/lib/ats_service_initialized
    chsh -s `which fish`
fi

# Execute the original command or keep container running
exec "$@"
