package it;

import com.google.api.gax.rpc.NotFoundException;
import static com.google.common.truth.Truth.assertThat;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstanceTemplatesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.flogger.GoogleLogger;
import com.google.pubsub.kafka.common.ConnectorUtils;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class StandaloneIT extends Base {
  ByteArrayOutputStream bout;
  PrintStream out;

  private static final Long KAFKA_PORT = Long.valueOf(9092);
  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();
  // TODO: System.getenv("GOOGLE_CLOUD_PROJECT")
  private static final String projectId = "loadtest-samarthsingal" ;
  // TODO: System.getenv("GOOGLE_CLOUD_PROJECT_NUMBER");
  private static final String projectNumber = "700946198918";
  private static final String sourceTopicId = "cps-source-topic-" + runId;
  private static final String sourceSubscriptionId = "cps-source-subscription-" + runId;
  private static final String sinkTopicId = "cps-sink-topic-" + runId;
  private static final String sinkTestKafkaTopic = "cps-sink-test-kafka-topic";
  private static final String sinkSubscriptionId = "cps-sink-subscription-" + runId;
  private static final String zone = "us-central1-b";
  private static final String instanceName = "kafka-it-" + runId;
  private static final String instanceTemplateName = "kafka-it-template-" + runId;
  private Boolean initialized = false;
  private Boolean cpsMessageReceived = false;

  private static final TopicName sourceTopicName = TopicName.of(projectId, sourceTopicId);
  private static final TopicName sinkTopicName = TopicName.of(projectId, sinkTopicId);
  private static final SubscriptionName sourceSubscriptionName =
      SubscriptionName.of(projectId, sourceSubscriptionId);
  private static final SubscriptionName sinkSubscriptionName =
      SubscriptionName.of(projectId, sinkSubscriptionId);

  Instance gceKafkaInstance;

  @Rule public Timeout globalTimeout = Timeout.seconds(600); // 10 minute timeout

  @Before
  public void setUp() throws Exception {
    bout = new ByteArrayOutputStream();
    out = new PrintStream(bout);
    System.setOut(out);

    if (initialized) {
      return;
    }

    findMavenHome();
    setupVersions();
    mavenPackage(workingDir);
    log.atInfo().log("Packaged connector jar.");

    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    uploadGCS(storage, cpsConnectorJarNameInGCS, cpsConnectorJarLoc);
    log.atInfo().log("Uploaded CPS connector jar to GCS.");

    uploadGCS(storage, cpsSinkConnectorPropertiesGCSName, testResourcesDirLoc + cpsSinkConnectorPropertiesName);
    log.atInfo().log("Uploaded CPS sink connector properties file to GCS.");


    initialized = true;

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
  }

  @After
  public void tearDown() throws Exception {
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
    assertThat(gceKafkaInstance.getNetworkInterfaces(0).getAccessConfigsList()).isNotEmpty();
    String instanceIpAddress = gceKafkaInstance.getNetworkInterfaces(0).getAccessConfigs(0)
        .getNatIP();
    Properties props = new Properties();
    props.put("bootstrap.servers", instanceIpAddress + ":" + KAFKA_PORT);
    props.put("linger.ms", 1);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    // Send message to Kafka topic.
    KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
    kafkaProducer.send(new ProducerRecord<String, String>(sinkTestKafkaTopic, "key0", "value0"));
    kafkaProducer.close();

    // Sleep.
    Thread.sleep(30 * 1000);

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
}
