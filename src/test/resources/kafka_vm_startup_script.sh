#!/bin/bash
set -x
sudo apt-get update
sudo apt-get install -yq wget openjdk-11-jdk maven

# Download connector JARs and properties files
GCS_BUCKET=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcs_bucket -H "Metadata-Flavor: Google")
CPS_CONNECTOR_JAR=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/cps_connector_jar_name -H "Metadata-Flavor: Google")
CPS_SINK_CONNECTOR_PROPERTIES=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/cps_sink_connector_properties_name -H "Metadata-Flavor: Google")
CPS_SOURCE_CONNECTOR_PROPERTIES=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/cps_source_connector_properties_name -H "Metadata-Flavor: Google")
GCS_DIR='gcs_resources'

mkdir $GCS_DIR
gsutil cp "gs://$GCS_BUCKET/$CPS_CONNECTOR_JAR" $GCS_DIR/
gsutil cp "gs://$GCS_BUCKET/$CPS_SINK_CONNECTOR_PROPERTIES" $GCS_DIR/
gsutil cp "gs://$GCS_BUCKET/$CPS_SOURCE_CONNECTOR_PROPERTIES" $GCS_DIR/
echo "Files in $GCS_DIR: "
ls -l $GCS_DIR/

# Prepare properties files for this run
RUN_ID=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/run_id -H "Metadata-Flavor: Google")
PROJECT_NAME=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/project_id -H "Metadata-Flavor: Google")

sed -i "s/<runId>/$RUN_ID/g" $GCS_DIR/*.properties
sed -i "s/<projectName>/$PROJECT_NAME/g" $GCS_DIR/*.properties

# Install and run Kafka brokers
KAFKA_VERSION=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/kafka_version -H "Metadata-Flavor: Google")
SCALA_VERSION=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/scala_version -H "Metadata-Flavor: Google")
KAFKA_URL="https://dlcdn.apache.org/kafka/$KAFKA_VERSION/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz"
KAFKA_DIR="kafka_$SCALA_VERSION-$KAFKA_VERSION"
EXTERNAL_IP=$(curl http://metadata/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H "Metadata-Flavor: Google")

wget $KAFKA_URL
tar -xzf "$KAFKA_DIR.tgz"
sed -i "s@#advertised.listeners@advertised.listeners@g" $KAFKA_DIR/config/server.properties
sed -i "s@your.host.name@$EXTERNAL_IP@g" $KAFKA_DIR/config/server.properties
$KAFKA_DIR/bin/zookeeper-server-start.sh $KAFKA_DIR/config/zookeeper.properties &
$KAFKA_DIR/bin/kafka-server-start.sh $KAFKA_DIR/config/server.properties &

# Run connectors
sed -i "s@#plugin.path=@plugin.path=$(pwd)\/$GCS_DIR@g" $KAFKA_DIR/config/connect-standalone.properties
## Create kafka topics for connectors
$KAFKA_DIR/bin/kafka-topics.sh --create --topic 'cps-sink-test-kafka-topic' --bootstrap-server localhost:9092
$KAFKA_DIR/bin/kafka-topics.sh --create --topic 'cps-source-test-kafka-topic' --bootstrap-server localhost:9092
## Start connectors
$KAFKA_DIR/bin/connect-standalone.sh $KAFKA_DIR/config/connect-standalone.properties $GCS_DIR/$CPS_SINK_CONNECTOR_PROPERTIES $GCS_DIR/$CPS_SOURCE_CONNECTOR_PROPERTIES &

set +x