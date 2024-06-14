#!/usr/bin/env bash

#Poll Kafka Connect until it is up
while true
do
    echo "Pinging Connect Rest Endpoint"
    CONNECT_PING=$(curl localhost:8083 | grep "version")
    if [[ $CONNECT_PING != "" ]]; then
        break
    fi
    sleep 30
done
#Once Kafka Connect is up, if the PubSub Lite migration job 
#does not yet exist, submit the Job
CONNECT_JOBS=$(curl localhost:8083/connectors | grep "__PUBSUB_LITE_JOB_NAME__")
if [[ $CONNECT_JOBS == "" ]]; then
    echo "No Connect Job found, posting Job"
    curl -H "Content-Type: application/json" -H "Accept: application/json" --data "@/opt/kafka/config/PSL_job.json" localhost:8083/connectors
fi
