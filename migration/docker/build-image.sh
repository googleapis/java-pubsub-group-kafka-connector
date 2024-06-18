SELF_DIR="$(dirname $(readlink -f $0))"
SECRETS_DIR="$(dirname ${SELF_DIR})/.gcp"
docker build --platform=linux/amd64 --file Dockerfile --tag psl-to-gmk:latest \
  --secret id=gmk_sasl_service_account,src="${SECRETS_DIR}/gmk_sasl_service_account" \
  --secret id=gmk_sasl_service_account_key,src="${SECRETS_DIR}/gmk_sasl_service_account_key" \
  --secret id=gmk_bootstrap_servers,src="${SECRETS_DIR}/gmk_bootstrap_servers" \
  --secret id=kafka_sink_topic,src="${SECRETS_DIR}/kafka_sink_topic" \
  --secret id=kafka_connect_group_id,src="${SECRETS_DIR}/kafka_connect_group_id" \
  --secret id=pubsub_lite_gcp_project,src="${SECRETS_DIR}/pubsub_lite_gcp_project" \
  --secret id=pubsub_lite_gcp_location,src="${SECRETS_DIR}/pubsub_lite_gcp_location" \
  --secret id=pubsub_lite_gcp_subscription,src="${SECRETS_DIR}/pubsub_lite_gcp_subscription" \
  --secret id=pubsub_lite_job_name,src="${SECRETS_DIR}/pubsub_lite_job_name" \
  --secret id=kafka_config_storage_topic,src="${SECRETS_DIR}/kafka_config_storage_topic" \
  --secret id=kafka_offset_storage_topic,src="${SECRETS_DIR}/kafka_offset_storage_topic" \
  --secret id=kafka_status_storage_topic,src="${SECRETS_DIR}/kafka_status_storage_topic" \
  --secret id=kafka_ssl_truststore_location,src="${SECRETS_DIR}/kafka_ssl_truststore_location" \
  --secret id=kafka_ssl_truststore_password,src="${SECRETS_DIR}/kafka_ssl_truststore_password" \
  --no-cache .
