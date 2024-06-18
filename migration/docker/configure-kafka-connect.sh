#!/usr/bin/env bash
# All the variables must be supplied as environment variables for this script
# Update Kafka Connect Sink config
sed -i -e "s#__KAFKA_BOOTSTRAP_SERVERS__#${KAFKA_BOOTSTRAP_SERVERS}#g;" \
    "${KAFKA_CONNECT_CONFIG_FILE}"
# Update Kafka Connect internal topics config
sed -i -e "s#__KAFKA_CONFIG_STORAGE_TOPIC__#${KAFKA_CONFIG_STORAGE_TOPIC}#g; s#__KAFKA_OFFSET_STORAGE_TOPIC__#${KAFKA_OFFSET_STORAGE_TOPIC}#g; s#__KAFKA_STATUS_STORAGE_TOPIC__#${KAFKA_STATUS_STORAGE_TOPIC}#g" \
    "${KAFKA_CONNECT_CONFIG_FILE}"
# Update Kafka Connect group id and Kafka Connect plugins directory. Kafka Connect group id needs to be unique and must not conflict with the consumer group ids
sed -i -e "s#__KAFKA_CONNECT_GROUP_ID__#${KAFKA_CONNECT_GROUP_ID}#g; s#__KAFKA_PLUGINS_DIR__#${KAFKA_PLUGINS_DIR}#g" \
    "${KAFKA_CONNECT_CONFIG_FILE}"
# Update Kafka Connect SASL config
sed -i -e "s#__KAFKA_SASL_SERVICE_ACCOUNT__#${KAFKA_SASL_SERVICE_ACCOUNT}#g; s#__KAFKA_SASL_SERVICE_ACCOUNT_KEY__#${KAFKA_SASL_SERVICE_ACCOUNT_KEY}#g" \
    "${KAFKA_CONNECT_CONFIG_FILE}"
# Update Kafka Connect SSL truststore config
sed -i -e "s#__KAFKA_SSL_TRUSTSTORE_LOCATION__#${KAFKA_SSL_TRUSTSTORE_LOCATION}#g; s#__KAFKA_SSL_TRUSTSTORE_PASSWORD__#${KAFKA_SSL_TRUSTSTORE_PASSWORD}#g" \
    "${KAFKA_CONNECT_CONFIG_FILE}"

#Update PubSub Lite Job File
sed -i -e "s#__PUBSUB_LITE_JOB_NAME__#${PUBSUB_LITE_JOB_NAME}#g; s#__KAFKA_SINK_TOPIC__#${KAFKA_SINK_TOPIC}#g; s#__PUBSUB_LITE_GCP_PROJECT__#${PUBSUB_LITE_GCP_PROJECT}#g; s#__PUBSUB_LITE_GCP_LOCATION__#${PUBSUB_LITE_GCP_LOCATION}#g; s#__PUBSUB_LITE_GCP_SUBSCRIPTION__#${PUBSUB_LITE_GCP_SUBSCRIPTION}#g;" \
    "${PUBSUB_LITE_JOB_FILE}"

#Update PSL Job Start Script
sed -i -e "s#__PUBSUB_LITE_JOB_NAME__#${PUBSUB_LITE_JOB_NAME}#g;" \
    "${PSL_JOB_STARTUP_SCRIPT}"