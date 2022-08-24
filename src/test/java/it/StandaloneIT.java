package it;

import static com.google.common.truth.Truth.assertThat;

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
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.kafka.common.ConnectorUtils;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class StandaloneIT extends Base {
  ByteArrayOutputStream bout;
  PrintStream out;

  private static final Long KAFKA_PORT = Long.valueOf(9092);
  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();
  private static final String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
  private static final String projectNumber = System.getenv("GOOGLE_CLOUD_PROJECT_NUMBER");
  private static final String sourceTopicId = "cps-source-topic-" + runId;
  private static final String sourceSubscriptionId = "cps-source-subscription-" + runId;
  private static final String sourceKafkTopic = "cps-source-test-kafka-topic";
  private static final String sinkTopicId = "cps-sink-topic-" + runId;
  private static final String sinkTestKafkaTopic = "cps-sink-test-kafka-topic";
  private static final String sinkSubscriptionId = "cps-sink-subscription-" + runId;
  private static final String zone = "us-central1-b";
  private static final String instanceName = "kafka-it-" + runId;
  private static final String instanceTemplateName = "kafka-it-template-" + runId;
  private static AtomicBoolean initialized = new AtomicBoolean(false);
  private static AtomicBoolean cleaned = new AtomicBoolean(false);
  private Boolean cpsMessageReceived = false;

  private static final TopicName sourceTopicName = TopicName.of(projectId, sourceTopicId);
  private static final TopicName sinkTopicName = TopicName.of(projectId, sinkTopicId);
  private static final SubscriptionName sourceSubscriptionName =
      SubscriptionName.of(projectId, sourceSubscriptionId);
  private static final SubscriptionName sinkSubscriptionName =
      SubscriptionName.of(projectId, sinkSubscriptionId);

  private static Instance gceKafkaInstance;
  private static String kafkaInstanceIpAddress;

  @Rule public Timeout globalTimeout = Timeout.seconds(600); // 10 minute timeout

  @Before
  public void setUp() throws Exception {
    bout = new ByteArrayOutputStream();
    out = new PrintStream(bout);
    System.setOut(out);

    if (initialized.get()) {
      return;
    }
    initialized.getAndSet( true);

    findMavenHome();
    setupVersions();
    mavenPackage(workingDir);
    log.atInfo().log("Packaged connector jar.");

    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    uploadGCS(storage, cpsConnectorJarNameInGCS, cpsConnectorJarLoc);
    log.atInfo().log("Uploaded CPS connector jar to GCS.");

    uploadGCS(storage, cpsSinkConnectorPropertiesGCSName, testResourcesDirLoc + cpsSinkConnectorPropertiesName);
    log.atInfo().log("Uploaded CPS sink connector properties file to GCS.");

    uploadGCS(storage, cpsSourceConnectorPropertiesGCSName, testResourcesDirLoc + cpsSourceConnectorPropertiesName);
    log.atInfo().log("Uploaded CPS source connector properties file to GCS.");

    setupCpsResources();
    setupGceInstance();
  }

  protected void setupCpsResources() throws IOException {
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      topicAdminClient.createTopic(sourceTopicName);
      topicAdminClient.createTopic(sinkTopicName);
      log.atInfo().log("Created source and sink topics.");
    }

    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      subscriptionAdminClient.createSubscription(
          Subscription.newBuilder()
              .setName(sourceSubscriptionName.toString())
              .setTopic(sourceTopicName.toString())
              .build());
      subscriptionAdminClient.createSubscription(
          Subscription.newBuilder()
              .setName(sinkSubscriptionName.toString())
              .setTopic(sinkTopicName.toString())
              .build());
      log.atInfo().log("Created source and sink subscriptions");
    }
  }

  protected void setupGceInstance()
      throws IOException, ExecutionException, InterruptedException, TimeoutException {

    createInstanceTemplate(projectId, projectNumber, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance template.");

    createInstanceFromTemplate(projectId, zone, instanceName, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance from instance template");

    this.gceKafkaInstance = getInstance(projectId, zone, instanceName);
    this.kafkaInstanceIpAddress = gceKafkaInstance.getNetworkInterfaces(0).getAccessConfigs(0)
        .getNatIP();
    log.atInfo().log("Kafka GCE Instance: " + gceKafkaInstance.getSelfLink() + " " + gceKafkaInstance.getDescription());

  }

  @After
  public void tearDown() throws Exception {
    if (cleaned.get()) {
      return;
    }
    cleaned.getAndSet(true);
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      try {
        subscriptionAdminClient.deleteSubscription(sourceSubscriptionName);
        subscriptionAdminClient.deleteSubscription(sinkSubscriptionName);
        log.atInfo().log("Deleted source and sink subscriptions.");
      } catch (NotFoundException ignored) {
        log.atInfo().log("Ignore. No topics found.");
      }
    }

    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      topicAdminClient.deleteTopic(sourceTopicName.toString());
      topicAdminClient.deleteTopic(sinkTopicName.toString());
      log.atInfo().log("Deleted source and sink topics");
    } catch (NotFoundException ignored) {
      log.atInfo().log("Ignore. No subscriptions found.");
    }

    // TODO: cleanup instance
    try (InstancesClient instancesClient = InstancesClient.create()) {
      instancesClient.deleteAsync(projectId, zone, instanceName).get(3, TimeUnit.MINUTES);
    }
    log.atInfo().log("Deleted Compute Engine instance.");

    try (InstanceTemplatesClient instanceTemplatesClient = InstanceTemplatesClient.create()) {
      instanceTemplatesClient.deleteAsync(projectId, instanceTemplateName).get(3, TimeUnit.MINUTES);
    }
    log.atInfo().log("Deleted Compute Engine instance template.");

    System.setOut(null);
  }

  @Test
  public void testCpsSinkConnector() throws Exception {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaInstanceIpAddress + ":" + KAFKA_PORT);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    // Send message to Kafka topic.
    KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
    Future<RecordMetadata> send_future =  kafkaProducer.send(new ProducerRecord<String, String>(sinkTestKafkaTopic, "key0", "value0"));
    kafkaProducer.flush();
    send_future.get();
    kafkaProducer.metrics().forEach((metricName, metric) -> { if(metricName.name() == "record-send-total") { log.atInfo().log("record-send-total: " + metric.metricValue().toString());}});
    kafkaProducer.close();

    // Sleep.
    Thread.sleep(10 * 1000);

    // Subscribe to messages.
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, sinkSubscriptionId);

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          assertThat(message.getData().toStringUtf8()).isEqualTo("value0");
          assertThat(
              message.getAttributesMap().get(ConnectorUtils.CPS_MESSAGE_KEY_ATTRIBUTE)).isEqualTo(
              "key0");
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

  // @Test
  // public void testCpsSourceConnector() throws Exception {
  //   // Publish to CPS topic
  //   ProjectTopicName sourceTopic = ProjectTopicName.of(projectId, sourceTopicId);
  //   Publisher publisher = Publisher.newBuilder(sourceTopic).build();
  //   PubsubMessage msg0 = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("msg0")).build();
  //   ApiFuture<String> publishFuture = publisher.publish(msg0);
  //   ApiFutures.addCallback(
  //       publishFuture,
  //       new ApiFutureCallback<String>() {
  //
  //         @Override
  //         public void onFailure(Throwable throwable) {
  //           if (throwable instanceof ApiException) {
  //             ApiException apiException = ((ApiException) throwable);
  //             // details on the API exception
  //             System.out.println(apiException.getStatusCode().getCode());
  //             System.out.println(apiException.isRetryable());
  //           }
  //           Assert.fail("Error publishing message : " + msg0);
  //         }
  //
  //         @Override
  //         public void onSuccess(String messageId) {
  //           // Once published, returns server-assigned message ids (unique within the topic)
  //           System.out.println("Published message ID: " + messageId);
  //         }
  //       },
  //       MoreExecutors.directExecutor());
  //
  //   // Sleep for 10s.
  //   Thread.sleep(10*1000);
  //
  //   // Consume from Kafka connect.
  //   Properties consumer_props = new Properties();
  //   consumer_props.setProperty("bootstrap.servers", kafkaInstanceIpAddress + ":" + KAFKA_PORT);
  //   consumer_props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
  //   consumer_props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
  //   consumer_props.setProperty("group.id", "test");
  //   KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumer_props);
  //   kafkaConsumer.subscribe(List.of(sourceKafkTopic));
  //   ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
  //   Iterator<ConsumerRecord<String, String>> recordIterator = records.records(sourceKafkTopic).iterator();
  //   assertThat(recordIterator.hasNext()).isTrue();
  //   assertThat(recordIterator.next().value()).isEqualTo("msg0");
  // }
}
