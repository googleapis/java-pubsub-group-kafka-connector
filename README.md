### Introduction

The Google Cloud Pub/Sub Group Kafka Connector library provides Google Cloud
Platform (GCP) first-party connectors for Pub/Sub products with
[Kafka Connect](http://kafka.apache.org/documentation.html#connect).
You can use the library transmit data from [Kafka](http://kafka.apache.org) to
[Cloud Pub/Sub](https://cloud.google.com/pubsub/docs/) or
[Pub/Sub Lite](https://cloud.google.com/pubsub/lite/docs) and vice versa.

- `CloudPubSubSinkConnector` is a sink connector that reads records from Kafka
and publishes them to Cloud Pub/Sub.
- `CloudPubSubSourceConnector` is a source connector that reads messages from
Cloud Pub/Sub and writes them to Kafka.
- `PubSubLiteSinkConnector` is a sink connector that reads records from Kafka
and publishes them to Pub/Sub Lite.
- `PubSubLiteSourceConnector` is a source connector that reads messages from
Pub/Sub Lite and writes them to Kafka.

### Prerequisites

You must have a GCP project in order to use Cloud Pub/Sub or Pub/Sub Lite.

Follow these [setup steps](https://cloud.google.com/pubsub/docs/publish-receive-messages-client-library#before-you-begin)
for Pub/Sub before doing the [quickstart](#quickstart).

Follow these [setup steps](https://cloud.google.com/pubsub/lite/docs/publish-receive-messages-console#before-you-begin)
for Pub/Sub Lite before doing the [quickstart](#quickstart).
    
### Quickstart

In this quickstart, you will learn how to send data from a Kafka topic to
a Pub/Sub or Pub/Sub Lite topic and vice versa, using a Kafka cluster running
locally in standalone mode (single process).

1. Follow the [Kafka quickstart](https://kafka.apache.org/quickstart) to
   download Kafka, start the Kafka environment, and create a Kafka topic.
   > Note: Please use the same Kafka major version as that used by the connector.
   > Otherwise, the connector may not work properly. Check the Kafka version
   > used by the connector in [pom.xml](pom.xml).
2. [Acquire](#acquire-the-connector) the connector jar.
3. Update your Kafka Connect configurations.

   Open `/config/connect-standalone.properties` in the Kafka download folder.
   Add the filepath of the downloaded connector jar to `plugin.path` and
   uncomment the line if needed. In addition, because the connector is using
   a Kafka cluster in standalone mode, include `offset.storage.file.filename`
   with a valid filename to store offset data in.
4. Create a pair of Pub/Sub or Pub/Sub Lite topic and subscription.

   - `CloudPubSubSinkConnector` and `CloudPubSubSourceConnector`
     - Create a pair of Pub/Sub [topic](https://cloud.google.com/pubsub/docs/admin#create_a_topic)
     and [subscription](https://cloud.google.com/pubsub/docs/create-subscription#pull_subscription)
   - `PubSubLiteSinkConnector` and `PubSubLiteSinkConnector`
     - Craete a pair of Pub/Sub Lite [topic](https://cloud.google.com/pubsub/lite/docs/topics#create_a_lite_topic)
     and [subscription](https://cloud.google.com/pubsub/lite/docs/subscriptions#create_a_lite_subscription).

5. Update the connector configurations.

   Open the connector configuration files at [/config](/config). Update
   variables labeled `TODO (developer)` with appropriate input.

   - `CloudPubSubSinkConnector`
       1. Open [`cps-sink-connector.properties`](/config/cps-sink-connector.properties).
       2. Update `topics`, `cps.project`, and `cps.topic`.

   - `CloudPubSubSourceConnector`
       1. Open [`cps-source-connector.properties`](/config/cps-source-connector.properties).
       2. Update `kafka.topic`, `cps.project`, and `cps.subscription`.

   - `PubSubLiteSinkConnector`
       1. Open [`pubsub-lite-sink-connector.properties`](/config/pubsub-lite-sink-connector.properties).
       2. Update `topics`, `pubsublite.project`, `pubsublite.location` and `pubsublite.topic`.

   - `PubSubLiteSinkConnector`
       1. Open [`pubsub-lite-source-connector.properties`](/config/pubsub-lite-source-connector.properties).
       2. Update `kafka.topic`, `pubsublite.project`, `pubsublite.location` and `pubsublite.subscription`.

6. Run the following command to start the appropriate sink or source connector.
   You can run multiple connector tasks at the same time.
   ```sh
   > bin/connect-standalone.sh \
     config/connect-standalone.properties \
     path/to/pubsub/sink/connector.properties [source.connector.properties ...]
   ```

7. Test the connector.

   - `CloudPubSubSinkConnector`
     1. Follow the instructions ihe [Kafka quickstart](https://kafka.apache.org/quickstart)
     to publish a message to the Kafka topic.
     2. [Pull](https://cloud.google.com/pubsub/docs/publish-receive-messages-console#pull_the_message_from_the_subscription)
     the message from your Pub/Sub subscription. 

   - `CloudPubSubSourceConnector`
     1. [Publish](https://cloud.google.com/pubsub/docs/publish-receive-messages-console#publish_a_message_to_the_topic)
     a message to your Pub/Sub topic.
     2. Follow the instructions ihe [Kafka quickstart](https://kafka.apache.org/quickstart)
     to read the message from your Kafka topic.

   - `PubSubLiteSinkConnector`
     1. Follow the instructions ihe [Kafka quickstart](https://kafka.apache.org/quickstart)
     to publish a message to the Kafka topic.
     2. [Pull](https://cloud.google.com/pubsub/docs/publish-receive-messages-console#pull_the_message_from_the_subscription)
     the message from your Pub/Sub Lite subscription.

   - `PubSubLiteSinkConnector`
     1. [Publish](https://cloud.google.com/pubsub/docs/publish-receive-messages-console#publish_a_message_to_the_topic)
     a message to your Pub/Sub Lite topic.
     2. Follow the instructions ihe [Kafka quickstart](https://kafka.apache.org/quickstart)
     to read the message from your Kafka topic.

### Acquire the connector

A pre-built uber-jar is available for download on the
[releases page](https://github.com/googleapis/java-pubsub-group-kafka-connector/releases).

You can also [build](#build-the-connector) the connector from head.

### Run the connector

To run this connector in standalone mode, follow these general steps:

1. Copy the connector jar where you will run Kafka Connect. 

2. Create a configuration file for your Kafka Connect instance. Make sure
   to include the filepath to the connector jar in `plugin.path`. See general
   information on Kafka Connect in [Kafka Users Guide](http://kafka.apache.org/documentation.html#connect_running).

3. Make a copy of the connector configuration files at [/config](/config)
   and update the configuration options accordingly.

4. Start Kafka Connect with your connector with the following command:
   ```shell
   > bin/connect-standalone.sh config/connect-standalone.properties connector1.properties [connector2.properties ...]
   ```

5. If running the Kafka Connect behind a proxy, export the `KAFKA_OPTS`
   variable with options for connecting around the proxy.
   ```shell
   > export KAFKA_OPTS="-Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port> -Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>"
   ```

When running the connector on a Kafka cluster in distributed mode, "the
connector configurations are not passed on the command line. Instead, use the
REST API described below to create, modify, and destroy connectors"
([Kafka Users Guide](http://kafka.apache.org/documentation.html#connect_running)).

### Pub/Sub connector configs

In addition to the configs supplied by the Kafka Connect API, the Pub/Sub
connector supports the following configurations:

#### Source Connector

| Config                                 | Value Range                                                        | Default                      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|----------------------------------------|--------------------------------------------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| cps.subscription                       | String                                                             | REQUIRED (No default)        | The Pub/Sub subscription ID, e.g. "baz" for subscription "/projects/bar/subscriptions/baz".                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| cps.project                            | String                                                             | REQUIRED (No default)        | The project containing the Pub/Sub subscription, e.g. "bar" from above.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| cps.endpoint                           | String                                                             | "pubsub.googleapis.com:443"  | The [Pub/Sub endpoint](https://cloud.google.com/pubsub/docs/reference/service_apis_overview#service_endpoints) to use.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| kafka.topic                            | String                                                             | REQUIRED (No default)        | The Kafka topic which will receive messages from the Pub/Sub subscription.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| cps.maxBatchSize                       | Integer                                                            | 100                          | The maximum number of messages per batch in a pull request to Pub/Sub.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| cps.makeOrderingKeyAttribute           | Boolean                                                            | false                        | When true, copy the ordering key to the set of attributes set in the Kafka message.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| kafka.key.attribute                    | String                                                             | null                         | The Pub/Sub message attribute to use as a key for messages published to Kafka. If set to "orderingKey", use the message's ordering key.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| kafka.partition.count                  | Integer                                                            | 1                            | The number of Kafka partitions for the Kafka topic in which messages will be published to. NOTE: this parameter is ignored if partition scheme is "kafka_partitioner".                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| kafka.partition.scheme                 | round_robin, hash_key, hash_value, kafka_partitioner, ordering_key | round_robin                  | The scheme for assigning a message to a partition in Kafka. The scheme "round_robin" assigns partitions in a round robin fashion, while the schemes "hash_key" and "hash_value" find the partition by hashing the message key and message value respectively. "kafka_partitioner" scheme delegates partitioning logic to Kafka producer, which by default detects number of partitions automatically and performs either murmur hash based partition mapping or round robin depending on whether message key is provided or not. "ordering_key" uses the hash code of a message's ordering key. If no ordering key is present, uses "round_robin". |
| gcp.credentials.file.path              | String                                                             | Optional                     | The file path, which stores GCP credentials. If not defined, GOOGLE_APPLICATION_CREDENTIALS env is used.If specified, use the explicitly handed credentials. Consider using the externalized secrets feature in Kafka Connect for passing the value.                                                                                                                                                                                                                                                                                                                                                                                               | 
| gcp.credentials.json                   | String                                                             | Optional                     | GCP credentials JSON blob. If specified, use the explicitly handed credentials. Consider using the externalized secrets feature in Kafka Connect for passing the value.                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | 
| kafka.record.headers                   | Boolean                                                            | false                        | Use Kafka record headers to store Pub/Sub message attributes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| cps.streamingPull.enabled              | Boolean                                                            | false                        | Whether to use streaming pull for the connector to connect to Pub/Sub. If provided, cps.maxBatchSize is ignored.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| cps.streamingPull.flowControlMessages  | Long                                                               | 1,000                        | The maximum number of outstanding messages per task when using streaming pull.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| cps.streamingPull.flowControlBytes     | Long                                                               | 100L * 1024 * 1024 (100 MiB) | The maximum number of outstanding message bytes per task when using streaming pull.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| cps.streamingPull.parallelStreams      | Integer                                                            | 1                            | The maximum number of outstanding message bytes per task when using streaming pull.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| cps.streamingPull.maxAckExtensionMs    | Long                                                               | 0                            | The maximum number of milliseconds the subscribe deadline will be extended to in milliseconds when using streaming pull. A value of `0` implies the java-pubsub library default value.                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| cps.streamingPull.maxMsPerAckExtension | Long                                                               | 0                            | The maximum number of milliseconds to extend the subscribe deadline for at a time when using streaming pull. A value of `0` implies the java-pubsub library default value.                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |

#### Sink Connector

| Config                     | Value Range                   | Default                     | Description                                                                                                                                                                                                                                                                                                                         |
|----------------------------|-------------------------------|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| cps.topic                  | String                        | REQUIRED (No default)       | The Pub/Sub topic ID, e.g. "foo" for topic "/projects/bar/topics/foo".                                                                                                                                                                                                                                                              |
| cps.project                | String                        | REQUIRED (No default)       | The project containing the Pub/Sub topic, e.g. "bar" from above.                                                                                                                                                                                                                                                                    |
| cps.endpoint               | String                        | "pubsub.googleapis.com:443" | The [Pub/Sub endpoint](https://cloud.google.com/pubsub/docs/reference/service_apis_overview#service_endpoints) to use.                                                                                                                                                                                                              |
| maxBufferSize              | Integer                       | 100                         | The maximum number of messages that can be received for the messages on a topic partition before publishing them to Pub/Sub.                                                                                                                                                                                                        |
| maxBufferBytes             | Long                          | 10,000,000                  | The maximum number of bytes that can be received for the messages on a topic partition before publishing them to Pub/Sub.                                                                                                                                                                                                           |
| maxOutstandingRequestBytes | Long                          | Long.MAX_VALUE              | The maximum number of total bytes that can be outstanding (including incomplete and pending batches) before the publisher will block further publishing.                                                                                                                                                                            |
| maxOutstandingMessages     | Long                          | Long.MAX_VALUE              | The maximum number of messages that can be outstanding (including incomplete and pending batches) before the publisher will block further publishing.                                                                                                                                                                               |
| maxDelayThresholdMs        | Integer                       | 100                         | The maximum amount of time to wait to reach maxBufferSize or maxBufferBytes before publishing outstanding messages to Pub/Sub.                                                                                                                                                                                                      |
| maxRequestTimeoutMs        | Integer                       | 10,000                      | The timeout for individual publish requests to Pub/Sub.                                                                                                                                                                                                                                                                             |
| maxTotalTimeoutMs          | Integer                       | 60,000                      | The total timeout for a call to publish (including retries) to Pub/Sub.                                                                                                                                                                                                                                                             |
| maxShutdownTimeoutMs       | Integer                       | 60,000                      | The maximum amount of time to wait for a publisher to shutdown when stopping task in Kafka Connect.                                                                                                                                                                                                                                 |
| gcp.credentials.file.path  | String                        | Optional                    | The file path, which stores GCP credentials. If not defined, GOOGLE_APPLICATION_CREDENTIALS env is used.                                                                                                                                                                                                                            |
| gcp.credentials.json       | String                        | Optional                    | GCP credentials JSON blob. If specified, use the explicitly handed credentials. Consider using the externalized secrets feature in Kafka Connect for passing the value.                                                                                                                                                             |
| metadata.publish           | Boolean                       | false                       | When true, include the Kafka topic, partition, offset, and timestamp as message attributes when a message is published to Pub/Sub.                                                                                                                                                                                                  |
| headers.publish            | Boolean                       | false                       | When true, include any headers as attributes when a message is published to Pub/Sub.                                                                                                                                                                                                                                                |
| orderingKeySource          | String (none, key, partition) | none                        | When set to "none", do not set the ordering key. When set to "key", uses a message's key as the ordering key. If set to "partition", converts the partition number to a String and uses that as the ordering key. Note that using "partition" should only be used for low-throughput topics or topics with thousands of partitions. |
| messageBodyName            | String                        | "cps_message_body"          | When using a struct or map value schema, this field or key name indicates that the corresponding value will go into the Pub/Sub message body.                                                                                                                                                                                       |

### Pub/Sub Lite connector configs

In addition to the configs supplied by the Kafka Connect API, the Pub/Sub Lite
connector supports the following configurations:

#### Source Connector

| Config                                     | Value Range | Default               | Description                                                                                                                    |
|--------------------------------------------|-------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------------|
| pubsublite.subscription                    | String      | REQUIRED (No default) | The Pub/Sub Lite subscription ID, e.g. "baz" for the subscription "/projects/bar/locations/europe-south7-q/subscriptions/baz". |
| pubsublite.project                         | String      | REQUIRED (No default) | The project containing the Pub/Sub Lite subscription, e.g. "bar" from above.                                                   |
| pubsublite.location                        | String      | REQUIRED (No default) | The location of the Pub/Sub Lite subscription, e.g. "europe-south7-q" from above.                                              |
| kafka.topic                                | String      | REQUIRED (No default) | The Kafka topic which will receive messages from Pub/Sub Lite.                                                                 |
| pubsublite.partition_flow_control.messages | Long        | Long.MAX_VALUE        | The maximum number of outstanding messages per Pub/Sub Lite partition.                                                         |
| pubsublite.partition_flow_control.bytes    | Long        | 20,000,000            | The maximum number of outstanding bytes per Pub/Sub Lite partition.                                                            |

#### Sink Connector

| Config              | Value Range | Default               | Description                                                                                           |
|---------------------|-------------|-----------------------|-------------------------------------------------------------------------------------------------------|
| pubsublite.topic    | String      | REQUIRED (No default) | The Pub/Sub Lite topic ID, e.g. "foo" for topic "/projects/bar/locations/europe-south7-q/topics/foo". |
| pubsublite.project  | String      | REQUIRED (No default) | The project containing the Pub/Sub Lite topic, e.g. "bar" from above.                                 |
| pubsublite.location | String      | REQUIRED (No default) | The location of the Pub/Sub Lite topic, e.g. "europe-south7-q" from above.                            |

### Shared miscellaneous configs

These configuraions are shared between both the Pub/Sub and Pub/Sub Lite Kafka
connector.

| Config                    | Value Range | Default  | Description                                                                                                                                                                                                                                                                  |
|---------------------------|-------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| gcp.credentials.file.path | String      | Optional | The file path, which stores GCP credentials. If not defined, the environment variable `GOOGLE_APPLICATION_CREDENTIALS` is used. If specified, use the explicitly handed credentials. Consider using the externalized secrets feature in Kafka Connect for passing the value. | 
| gcp.credentials.json      | String      | Optional | GCP credentials JSON blob. If specified, use the explicitly handed credentials. Consider using the externalized secrets feature in Kafka Connect for passing the value.                                                                                                      | 

### Schema support and data model

#### Pub/Sub Connector

The message data field of [`PubSubMessage`](https://cloud.google.com/pubsub/docs/reference/rpc/google.pubsub.v1#pubsubmessage)
is a [ByteString](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/ByteString)
object that translates well to and from the `byte[]` bodies of Kafka messages.
We recommend using a converter that produces primitive data types (i.e. integer,
float, string, or bytes types) where possible to avoid deserializing and
re-serializing the same message body.

Additionally, a Pub/Sub message cannot exceed 10 MB. We recommend checking your
`message.max.bytes` configuration to prevent possible errors.

The sink connector handles message conversion in the following way:

* Integer, float, string, and bytes types in Kafka messages are passed
  directly into the Pub/Sub message body as bytes.
* Map and struct types in Kafka messages are stored as Pub/Sub attributes.
  Pub/Sub attributes only supports string to string mapping. To make the
  connector as versatile as possible, the `toString()` method is called on
  objects passed in as the key or value for a map and the value for a struct.
  * If you set the `messageBodyName` configuration to a struct field or map
    key, the value of the structure field or the map value (of integer, byte,
    float, and array type) will be stored in the Pub/Sub message body as bytes.
* Only primitive array types are supported due to potential collisions
  of field names or keys of a struct/map array. The connector handles arrays in
  a fairly predictable fashion, where values are concatenated into a ByteString
  object.
* The record-level key for a Kafka message is stored in Pub/Sub message
  attributes as a string, with the name "key".

> **Note:** Pub/Sub message attributes have the following
> [limitations](https://cloud.google.com/pubsub/quotas#resource_limits):
>* Attributes per message: 100
>* Attribute key size: 256 bytes
>* Attribute value size: 1024 bytes
> 
> The connector will transform Kafka record-level message headers that meet
> these limitations and ignore those that don't.

The source connector handles the conversion from a Pub/Sub message into a Kafka
`SourceRecord` in a similar way:

* The source connector searches for `kafka.key.attribute` in the attributes of
  a Pub/Sub message. If found, it will be used as the Kafka message `key` as a
  string. Otherwise, the Kafka message `key` will be set to null.
* If a Pub/Sub message has no attributes, the message body will be stored as a
  byte[] object for the Kafka message `value`.
* If a Pub/Sub message contains attributes other than `kafka.key.attribute`,
  they will be assigned a struct schema. The message attribute keys will become
  struct field names, and the corresponding attribute values will become values
  of those struct fields. The message body will be transformed into a struct
  field of name `message` and of type bytes.
    * To carry forward the structure of data stored in Pub/Sub message
      attributes, we recommend using a converter that represents a struct schema
      type, like JsonConverter.

#### Pub/Sub Lite Connector

[Pub/Sub Lite messages](https://cloud.google.com/pubsub/lite/docs/reference/rpc/google.cloud.pubsublite.v1#pubsubmessage)
have the following structure:

```java
class Message {

  ByteString key;
  ByteString data;
  ListMultimap<String, ByteString> attributes;
  Optional<Timestamp> eventTime;
}
```

This table shows how each field in a Kafka `SinkRecord` maps to a Pub/Sub
Lite message by the sink connector:

| SinkRecord     | Message                                                      |
|----------------|--------------------------------------------------------------|
| key{Schema}    | key                                                          |
| value{Schema}  | data                                                         |
| headers        | attributes                                                   |
| topic          | attributes["x-goog-pubsublite-source-kafka-topic"]           |
| kafkaPartition | attributes["x-goog-pubsublite-source-kafka-partition"]       |
| kafkaOffset    | attributes["x-goog-pubsublite-source-kafka-offset"]          |
| timestamp      | eventTime                                                    |
| timestampType  | attributes["x-goog-pubsublite-source-kafka-event-time-type"] |

When a key, value or header value with a schema is encoded as a ByteString, the
following logic will be used:

- Null schemas are treated as `Schema.STRING_SCHEMA`
- Top-level BYTES payloads are unmodified
- Top-level STRING payloads are encoded using `copyFromUtf8`
- Top-level Integral payloads are converted using `copyFromUtf8(Long.toString(
  x.longValue()))`
- Top-level Floating point payloads are converted using `copyFromUtf8(
  Double.toString(x.doubleValue()))`
- All other payloads are encoded into a protobuf Value, then converted to a
  ByteString
    - Nested STRING fields are encoded into a protobuf Value
    - Nested BYTES fields are encoded to a protobuf Value holding the base64
      encoded bytes
    - Nested Numeric fields are encoded as a double into a protobuf Value
    - Maps with Array, Map, or Struct keys are not supported
        - BYTES keys in maps are base64 encoded
        - Integral keys are converted using `Long.toString(x.longValue())`
        - Floating point keys are converted using `Double.toString(
          x.doubleValue())`

The source connector performs a one-to-one mapping from
[`SequencedMessage`](https://cloud.google.com/pubsub/lite/docs/reference/rpc/google.cloud.pubsublite.v1#sequencedmessage)
fields to their Kafka `SourceRecord` counterparts.

Pub/Sub Lite message of empty `message.key` fields will have their field values
be converted to `null`, and they will be assigned to Kafka partitions using the
round-robin scheme. Messages with identical, non-empty keys will be routed to
the same Kafka partition.

| SequencedMessage     | SourceRecord field           | SourceRecord schema                                        |
|----------------------|------------------------------|------------------------------------------------------------|
| message.key          | key                          | BYTES                                                      |
| message.data         | value                        | BYTES                                                      |
| message.attributes   | headers                      | BYTES                                                      |
| `<source topic>`     | sourcePartition["topic"]     | String field in map                                        |
| `<source partition>` | sourcePartition["partition"] | Integer field in map                                       |
| cursor.offset        | sourceOffset["offset"]       | Long field in map                                          |
| message.event_time   | timestamp                    | long milliseconds since unix epoch if present              |
| publish_time         | timestamp                    | long milliseconds since unix epoch if no event_time exists |

### Build the connector

These instructions assume you are using [Maven](https://maven.apache.org/).

1. Clone this repository:
   ```shell
   > git clone https://github.com/googleapis/java-pubsub-group-kafka-connector
   ```

2. Package the connector jar:
   ```shell
   > mvn clean package -DskipTests=True
   ```
   You should see the resulting jar at `target/pubsub-group-kafka-connector-${VERSION}-SNAPSHOT.jar`
   on success.

## Versioning

This library follows [Semantic Versioning](http://semver.org/).

## Contributing

Contributions to this library are always welcome and highly encouraged.

See [CONTRIBUTING(CONTRIBUTING.md) for more information how to get started.

Please note that this project is released with a Contributor Code of Conduct. By
participating in this project you agree to abide by its terms.
See [Code of Conduct](CODE_OF_CONDUCT.md) for more information.

## License

Apache 2.0 - See [LICENSE](LICENSE) for more information.
