package it;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstanceTemplatesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectId;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.cloudpubsub.PublisherSettings;
import com.google.cloud.pubsublite.cloudpubsub.SubscriberSettings;
import com.google.cloud.pubsublite.proto.Subscription.DeliveryConfig;
import com.google.cloud.pubsublite.proto.Subscription.DeliveryConfig.DeliveryRequirement;
import com.google.cloud.pubsublite.proto.Topic;
import com.google.cloud.pubsublite.proto.Topic.PartitionConfig;
import com.google.cloud.pubsublite.proto.Topic.PartitionConfig.Capacity;
import com.google.cloud.pubsublite.proto.Topic.RetentionConfig;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import com.google.pubsub.kafka.common.ConnectorUtils;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class StandaloneIT extends Base {
  private static final Long KAFKA_PORT = Long.valueOf(9092);
  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();
  private static final String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
  private static final String projectNumber = System.getenv("GOOGLE_CLOUD_PROJECT_NUMBER");

  private static final String cpsSourceTopicId = "cps-source-topic-" + runId;
  private static final String cpsSourceSubscriptionId = "cps-source-subscription-" + runId;
  private static final String kafkaCpsSourceTestTopic = "cps-source-test-kafka-topic";
  private static final TopicName cpsSourceTopicName = TopicName.of(projectId, cpsSourceTopicId);
  private static final SubscriptionName cpsSourceSubscriptionName =
      SubscriptionName.of(projectId, cpsSourceSubscriptionId);

  private static final String kafkaCpsSinkTestTopic = "cps-sink-test-kafka-topic";
  private static final String cpsSinkSubscriptionId = "cps-sink-subscription-" + runId;
  private static final String cpsSinkTopicId = "cps-sink-topic-" + runId;
  private static final SubscriptionName cpsSinkSubscriptionName =
      SubscriptionName.of(projectId, cpsSinkSubscriptionId);
  private static final TopicName cpsSinkTopicName = TopicName.of(projectId, cpsSinkTopicId);

  private static final String kafkaPslSinkTestTopic = "psl-sink-test-topic";
  private static final String pslSinkTopicId = "psl-sink-topic-" + runId;
  private static final TopicPath pslSinkTopicPath =
      TopicPath.newBuilder()
          .setProject(ProjectId.of(projectId))
          .setLocation(CloudZone.of(CloudRegion.of(region), zone))
          .setName(com.google.cloud.pubsublite.TopicName.of(pslSinkTopicId))
          .build();
  private static final String pslSinkSubscriptionId = "psl-sink-subscription-" + runId;
  private static final SubscriptionPath pslSinkSubscriptionPath =
      SubscriptionPath.newBuilder()
          .setName(com.google.cloud.pubsublite.SubscriptionName.of(pslSinkSubscriptionId))
          .setProject(ProjectId.of(projectId))
          .setLocation(CloudZone.of(CloudRegion.of(region), zone))
          .build();

  private static final String kafkaPslSourceTestTopic = "psl-source-test-topic";
  private static final String pslSourceTopicId = "psl-source-topic-" + runId;
  private static final TopicPath pslSourceTopicPath =
      TopicPath.newBuilder()
          .setProject(ProjectId.of(projectId))
          .setLocation(CloudZone.of(CloudRegion.of(region), zone))
          .setName(com.google.cloud.pubsublite.TopicName.of(pslSourceTopicId))
          .build();
  private static final String pslSourceSubscriptionId = "psl-source-subscription-" + runId;
  private static final SubscriptionPath pslSourceSubscriptionPath =
      SubscriptionPath.newBuilder()
          .setName(com.google.cloud.pubsublite.SubscriptionName.of(pslSourceSubscriptionId))
          .setProject(ProjectId.of(projectId))
          .setLocation(CloudZone.of(CloudRegion.of(region), zone))
          .build();

  private static final String instanceName = "kafka-it-" + runId;
  private static final String instanceTemplateName = "kafka-it-template-" + runId;
  private static AtomicBoolean initialized = new AtomicBoolean(false);
  private static AtomicInteger testRunCount = new AtomicInteger(4);
  private static Boolean cpsMessageReceived = false;
  private static Boolean pslMessageReceived = false;

  private static Instance gceKafkaInstance;
  private static String kafkaInstanceIpAddress;

  private static void requireEnvVar(String varName) {
    assertNotNull(
        "Environment variable " + varName + " is required to perform these tests.",
        System.getenv(varName));
  }

  @Rule public Timeout globalTimeout = Timeout.seconds(20 * 60);

  @BeforeClass
  public static void checkRequirements() {
    requireEnvVar("GOOGLE_CLOUD_PROJECT");
    requireEnvVar("GOOGLE_CLOUD_PROJECT_NUMBER");
    requireEnvVar("BUCKET_NAME");
  }

  @Before
  public void setUp() throws Exception {
    if (!initialized.compareAndSet(false, true)) {
      return;
    }
    findMavenHome();
    setupVersions();
    mavenPackage(workingDir);
    log.atInfo().log("Packaged connector jar.");
    uploadGCSResources();
    setupCpsResources();
    setupPslResources();
    setupGceInstance();
  }

  protected void uploadGCSResources() throws Exception {
    // Upload the connector JAR and properties files to GCS.
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    uploadGCS(storage, connectorJarNameInGCS, cpsConnectorJarLoc);
    log.atInfo().log("Uploaded CPS connector jar to GCS.");

    uploadGCS(
        storage,
        cpsSinkConnectorPropertiesGCSName,
        testResourcesDirLoc + cpsSinkConnectorPropertiesName);
    log.atInfo().log("Uploaded CPS sink connector properties file to GCS.");

    uploadGCS(
        storage,
        cpsSourceConnectorPropertiesGCSName,
        testResourcesDirLoc + cpsSourceConnectorPropertiesName);
    log.atInfo().log("Uploaded CPS source connector properties file to GCS.");

    uploadGCS(
        storage,
        pslSinkConnectorPropertiesGCSName,
        testResourcesDirLoc + pslSinkConnectorPropertiesName);
    log.atInfo().log("Uploaded PSL sink connector properties file to GCS.");

    uploadGCS(
        storage,
        pslSourceConnectorPropertiesGCSName,
        testResourcesDirLoc + pslSourceConnectorPropertiesName);
    log.atInfo().log("Uploaded PSL source connector properties file to GCS.");
  }

  protected void setupCpsResources() throws IOException {
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      topicAdminClient.createTopic(cpsSourceTopicName);
      log.atInfo().log("Created CPS source topic: " + cpsSourceTopicName);
      topicAdminClient.createTopic(cpsSinkTopicName);
      log.atInfo().log("Created CPS sink topic: " + cpsSinkTopicName);
    }

    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      subscriptionAdminClient.createSubscription(
          Subscription.newBuilder()
              .setName(cpsSinkSubscriptionName.toString())
              .setTopic(cpsSinkTopicName.toString())
              .build());
      log.atInfo().log("Created CPS sink subscription: " + cpsSinkSubscriptionName);
      subscriptionAdminClient.createSubscription(
          Subscription.newBuilder()
              .setName(cpsSourceSubscriptionName.toString())
              .setTopic(cpsSourceTopicName.toString())
              .build());
      log.atInfo().log("Created CPS source subscription: " + cpsSourceSubscriptionName);
    }
  }

  protected void setupPslResources() throws Exception {
    try (AdminClient pslAdminClient =
        AdminClient.create(
            AdminClientSettings.newBuilder().setRegion(CloudRegion.of(region)).build())) {
      Topic sinkTopic =
          Topic.newBuilder()
              .setName(pslSinkTopicPath.toString())
              .setPartitionConfig(
                  PartitionConfig.newBuilder()
                      .setCount(2)
                      .setCapacity(
                          Capacity.newBuilder()
                              .setPublishMibPerSec(4)
                              .setSubscribeMibPerSec(4)
                              .build()))
              .setRetentionConfig(
                  RetentionConfig.newBuilder()
                      .setPerPartitionBytes(30 * 1024 * 1024 * 1024L)
                      .setPeriod(Durations.fromHours(1)))
              .build();
      sinkTopic = pslAdminClient.createTopic(sinkTopic).get();
      log.atInfo().log("Created PSL sink topic: " + pslSinkTopicPath);

      com.google.cloud.pubsublite.proto.Subscription pslSinkSubscription =
          com.google.cloud.pubsublite.proto.Subscription.newBuilder()
              .setDeliveryConfig(
                  DeliveryConfig.newBuilder()
                      .setDeliveryRequirement(DeliveryRequirement.DELIVER_AFTER_STORED))
              .setName(pslSinkSubscriptionPath.toString())
              .setTopic(pslSinkTopicPath.toString())
              .build();
      pslSinkSubscription = pslAdminClient.createSubscription(pslSinkSubscription).get();
      log.atInfo().log("Created PSL sink subscription: " + pslSinkSubscriptionPath.toString());

      Topic sourceTopic =
          Topic.newBuilder()
              .setName(pslSourceTopicPath.toString())
              .setPartitionConfig(
                  PartitionConfig.newBuilder()
                      .setCount(2)
                      .setCapacity(
                          Capacity.newBuilder()
                              .setPublishMibPerSec(4)
                              .setSubscribeMibPerSec(4)
                              .build()))
              .setRetentionConfig(
                  RetentionConfig.newBuilder()
                      .setPerPartitionBytes(30 * 1024 * 1024 * 1024L)
                      .setPeriod(Durations.fromHours(1)))
              .build();
      sourceTopic = pslAdminClient.createTopic(sourceTopic).get();
      log.atInfo().log("Created PSL source topic: " + pslSourceTopicPath);

      com.google.cloud.pubsublite.proto.Subscription pslSourceSubscription =
          com.google.cloud.pubsublite.proto.Subscription.newBuilder()
              .setDeliveryConfig(
                  DeliveryConfig.newBuilder()
                      .setDeliveryRequirement(DeliveryRequirement.DELIVER_AFTER_STORED))
              .setName(pslSourceSubscriptionPath.toString())
              .setTopic(pslSourceTopicPath.toString())
              .build();
      pslSourceSubscription = pslAdminClient.createSubscription(pslSourceSubscription).get();
      log.atInfo().log("Created PSL source subscription:  " + pslSinkSubscriptionPath.toString());
    }
  }

  protected void setupGceInstance()
      throws IOException, ExecutionException, InterruptedException, TimeoutException {

    createInstanceTemplate(projectId, projectNumber, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance template.");

    createInstanceFromTemplate(projectId, location, instanceName, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance from instance template");

    this.gceKafkaInstance = getInstance(projectId, location, instanceName);
    this.kafkaInstanceIpAddress =
        gceKafkaInstance.getNetworkInterfaces(0).getAccessConfigs(0).getNatIP();
    log.atInfo().log(
        "Kafka GCE Instance: "
            + gceKafkaInstance.getSelfLink()
            + " "
            + gceKafkaInstance.getDescription());
  }

  @After
  public void tearDown() throws Exception {
    if (testRunCount.decrementAndGet() > 0) {
      return;
    }
    log.atInfo().log("Attempting teardown!");
    Function<Runnable, Void> notFoundIgnoredClosureRunner =
        new Function<Runnable, Void>() {
          @Override
          public Void apply(Runnable runnable) {
            try {
              runnable.run();
            } catch (NotFoundException ignored) {
              log.atInfo().log(ignored.getMessage());
              log.atInfo().log("Ignored. Resource not found!");
            }
            return null;
          }
        };
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      notFoundIgnoredClosureRunner.apply(
          () -> {
            subscriptionAdminClient.deleteSubscription(cpsSinkSubscriptionName);
          });
      log.atInfo().log("Deleted CPS subscriptions.");
    }

    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      notFoundIgnoredClosureRunner.apply(
          () -> {
            topicAdminClient.deleteTopic(cpsSourceTopicName.toString());
          });
      notFoundIgnoredClosureRunner.apply(
          () -> {
            topicAdminClient.deleteTopic(cpsSinkTopicName.toString());
          });
      log.atInfo().log("Deleted CPS topics.");
    }

    try (AdminClient pslAdminClient =
        AdminClient.create(
            AdminClientSettings.newBuilder().setRegion(CloudRegion.of(region)).build())) {
      notFoundIgnoredClosureRunner.apply(
          () -> {
            pslAdminClient.deleteSubscription(pslSinkSubscriptionPath);
          });
      notFoundIgnoredClosureRunner.apply(
          () -> {
            pslAdminClient.deleteSubscription(pslSourceSubscriptionPath);
          });
      notFoundIgnoredClosureRunner.apply(
          () -> {
            pslAdminClient.deleteTopic(pslSinkTopicPath);
          });
      notFoundIgnoredClosureRunner.apply(
          () -> {
            pslAdminClient.deleteTopic(pslSourceTopicPath);
          });
      log.atInfo().log("Deleted PSL topics and subscriptions.");
    }

    try (InstancesClient instancesClient = InstancesClient.create()) {
      instancesClient.deleteAsync(projectId, location, instanceName).get(3, TimeUnit.MINUTES);
    }
    log.atInfo().log("Deleted Compute Engine instance.");

    try (InstanceTemplatesClient instanceTemplatesClient = InstanceTemplatesClient.create()) {
      instanceTemplatesClient.deleteAsync(projectId, instanceTemplateName).get(3, TimeUnit.MINUTES);
    }
    log.atInfo().log("Deleted Compute Engine instance template.");
  }

  @Test
  public void testCpsSinkConnector() throws Exception {

    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaInstanceIpAddress + ":" + KAFKA_PORT);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    // Send message to Kafka topic.
    KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
    Future<RecordMetadata> send_future =
        kafkaProducer.send(
            new ProducerRecord<String, String>(kafkaCpsSinkTestTopic, "key0", "value0"));
    kafkaProducer.flush();
    send_future.get();
    kafkaProducer
        .metrics()
        .forEach(
            (metricName, metric) -> {
              if (metricName.name() == "record-send-total") {
                log.atInfo().log("record-send-total: " + metric.metricValue().toString());
              }
            });
    kafkaProducer.close();

    // Sleep for 1min.
    Thread.sleep(60 * 1000);

    // Subscribe to messages.
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, cpsSinkSubscriptionId);

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          assertThat(message.getData().toStringUtf8()).isEqualTo("value0");
          assertThat(message.getAttributesMap().get(ConnectorUtils.CPS_MESSAGE_KEY_ATTRIBUTE))
              .isEqualTo("key0");
          this.cpsMessageReceived = true;
          consumer.ack();
        };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      subscriber.startAsync().awaitRunning();
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync();
    }
    assertThat(this.cpsMessageReceived).isTrue();
  }

  @Test
  public void testCpsSourceConnector() throws Exception {
    // Publish to CPS topic
    ProjectTopicName sourceTopic = ProjectTopicName.of(projectId, cpsSourceTopicId);
    Publisher publisher = Publisher.newBuilder(sourceTopic).build();
    PubsubMessage msg0 =
        PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("msg0")).build();
    ApiFuture<String> publishFuture = publisher.publish(msg0);
    ApiFutures.addCallback(
        publishFuture,
        new ApiFutureCallback<String>() {

          @Override
          public void onFailure(Throwable throwable) {
            if (throwable instanceof ApiException) {
              ApiException apiException = ((ApiException) throwable);
              // details on the API exception
              System.out.println(apiException.getStatusCode().getCode());
              System.out.println(apiException.isRetryable());
            }
            Assert.fail("Error publishing message : " + msg0);
          }

          @Override
          public void onSuccess(String messageId) {
            // Once published, returns server-assigned message ids (unique within the topic)
            System.out.println("Published message ID: " + messageId);
          }
        },
        MoreExecutors.directExecutor());

    // Sleep for 1min.
    Thread.sleep(60 * 1000);

    // Consume from Kafka connect.
    Properties consumer_props = new Properties();
    consumer_props.setProperty(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaInstanceIpAddress + ":" + KAFKA_PORT);
    consumer_props.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumer_props.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumer_props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "unittest");
    consumer_props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumer_props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumer_props);
    final boolean[] assignmentReceived = {false};
    kafkaConsumer.subscribe(
        Arrays.asList(kafkaCpsSourceTestTopic),
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> collection) {}

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> collection) {
            assignmentReceived[0] = true;
          }
        });
    LocalTime startTime = LocalTime.now();
    boolean messageReceived = false;
    try {
      while (Duration.between(startTime, LocalTime.now())
              .compareTo(Duration.of(2, ChronoUnit.MINUTES))
          < 0) {
        ConsumerRecords<String, String> records =
            kafkaConsumer.poll(Duration.of(1, ChronoUnit.SECONDS));
        if (!assignmentReceived[0]) {
          continue;
        }
        Iterator<ConsumerRecord<String, String>> recordIterator =
            records.records(kafkaCpsSourceTestTopic).iterator();
        assertThat(recordIterator.hasNext()).isTrue();
        assertThat(recordIterator.next().value()).isEqualTo("msg0");
        messageReceived = true;
        break;
      }
    } finally {
      kafkaConsumer.commitSync();
      kafkaConsumer.close();
    }
    assertThat(messageReceived).isTrue();
  }

  @Test
  public void testPslSinkConnector() throws Exception {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaInstanceIpAddress + ":" + KAFKA_PORT);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    // Send message to Kafka topic.
    KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
    Future<RecordMetadata> send_future =
        kafkaProducer.send(
            new ProducerRecord<String, String>(kafkaPslSinkTestTopic, "key0", "value0"));
    kafkaProducer.flush();
    send_future.get();
    kafkaProducer
        .metrics()
        .forEach(
            (metricName, metric) -> {
              if (metricName.name() == "record-send-total") {
                log.atInfo().log("record-send-total: " + metric.metricValue().toString());
              }
            });
    kafkaProducer.close();

    // Sleep for 1min.
    Thread.sleep(60 * 1000);

    // Subscribe to messages.
    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          log.atInfo().log("Received message: " + message);
          assertThat(message.getData().toStringUtf8()).isEqualTo("value0");
          assertThat(message.getOrderingKey()).isEqualTo("key0");
          this.pslMessageReceived = true;
          log.atInfo().log("this.pslMessageReceived: " + this.pslMessageReceived);
          consumer.ack();
        };

    com.google.cloud.pubsublite.cloudpubsub.Subscriber subscriber =
        com.google.cloud.pubsublite.cloudpubsub.Subscriber.create(
            SubscriberSettings.newBuilder()
                .setSubscriptionPath(pslSinkSubscriptionPath)
                .setReceiver(receiver)
                .setPerPartitionFlowControlSettings(
                    FlowControlSettings.builder()
                        .setBytesOutstanding(10 * 1024 * 1024L)
                        .setMessagesOutstanding(1000L)
                        .build())
                .build());
    try {
      subscriber.startAsync().awaitRunning();
      subscriber.awaitTerminated(120, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync();
    }
    assertThat(this.pslMessageReceived).isTrue();
  }

  @Test
  public void testPslSourceConnector() throws Exception {
    // Publish to CPS topic
    PublisherSettings publisherSettings =
        PublisherSettings.newBuilder().setTopicPath(pslSourceTopicPath).build();

    com.google.cloud.pubsublite.cloudpubsub.Publisher publisher =
        com.google.cloud.pubsublite.cloudpubsub.Publisher.create(publisherSettings);
    publisher.startAsync().awaitRunning();

    PubsubMessage msg0 =
        PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("msg0")).build();
    ApiFuture<String> publishFuture = publisher.publish(msg0);
    ApiFutures.addCallback(
        publishFuture,
        new ApiFutureCallback<String>() {

          @Override
          public void onFailure(Throwable throwable) {
            if (throwable instanceof ApiException) {
              ApiException apiException = ((ApiException) throwable);
              // details on the API exception
              log.atInfo().log(apiException.fillInStackTrace().toString());
            }
            Assert.fail("Error publishing message : " + msg0);
          }

          @Override
          public void onSuccess(String messageId) {
            // Once published, returns server-assigned message ids (unique within the topic)
            log.atInfo().log("Published message ID: " + messageId);
          }
        },
        MoreExecutors.directExecutor());

    // Sleep for 1min.
    Thread.sleep(60 * 1000);

    // Consume from Kafka connect.
    Properties consumer_props = new Properties();
    consumer_props.setProperty(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaInstanceIpAddress + ":" + KAFKA_PORT);
    consumer_props.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumer_props.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumer_props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "unittest");
    consumer_props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumer_props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumer_props);
    final boolean[] assignmentReceived = {false};
    kafkaConsumer.subscribe(
        Arrays.asList(kafkaPslSourceTestTopic),
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> collection) {}

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> collection) {
            assignmentReceived[0] = true;
          }
        });
    LocalTime startTime = LocalTime.now();
    boolean messageReceived = false;
    try {
      while (Duration.between(startTime, LocalTime.now())
              .compareTo(Duration.of(1, ChronoUnit.MINUTES))
          < 0) {
        ConsumerRecords<String, String> records =
            kafkaConsumer.poll(Duration.of(2, ChronoUnit.SECONDS));
        if (!assignmentReceived[0]) {
          continue;
        }
        Iterator<ConsumerRecord<String, String>> recordIterator =
            records.records(kafkaPslSourceTestTopic).iterator();
        assertThat(recordIterator.hasNext()).isTrue();
        assertThat(recordIterator.next().value()).isEqualTo("msg0");
        messageReceived = true;
        break;
      }
    } finally {
      kafkaConsumer.commitSync();
      kafkaConsumer.close();
    }
    assertThat(messageReceived).isTrue();
    publisher.stopAsync().awaitTerminated();
  }
}
