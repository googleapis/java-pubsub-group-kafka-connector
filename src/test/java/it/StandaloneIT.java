package it;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.compute.v1.InsertInstanceRequest;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.flogger.GoogleLogger;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class StandaloneIT extends Base {
  ByteArrayOutputStream bout;
  PrintStream out;

  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();

  private static final String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
  private static final String _suffix = UUID.randomUUID().toString().substring(0, 6);
  private static final String sourceTopicId = "source-topic-" + _suffix;
  private static final String sourceSubscriptionId = "source-subscription-" + _suffix;
  private static final String sinkTopicId = "sink-topic-" + _suffix;
  private static final String sinkSubscriptionId = "sink-subscription-" + _suffix;
  private static final String zone = "us-central1-b";
  private static final String instanceName = "kafka-it-" + _suffix;
  private static final String instanceTemplateName =
      String.format(
          "projects/%s/global/instanceTemplates/%s", projectId, "pubsub-kafka-it-template");
  private Boolean initialized = false;

  private static final TopicName sourceTopicName = TopicName.of(projectId, sourceTopicId);
  private static final TopicName sinkTopicName = TopicName.of(projectId, sinkTopicId);
  private static final SubscriptionName sourceSubscriptionName =
      SubscriptionName.of(projectId, sourceSubscriptionId);
  private static final SubscriptionName sinkSubscriptionName =
      SubscriptionName.of(projectId, sinkSubscriptionId);

  @Rule public Timeout globalTimeout = Timeout.seconds(600); // 10 minute timeout

  @Before
  public void setUp() throws Exception {
    bout = new ByteArrayOutputStream();
    out = new PrintStream(bout);
    System.setOut(out);

    if (initialized) {
      return;
    }

    log.atInfo().log("RunId is: %s", _suffix);
    findMavenHome();
    setupVersions();
    mavenPackage(workingDir);
    log.atInfo().log("Packaged connector jar.");
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    uploadGCS(storage, connectorJarNameInGCS, connectorJarLoc);
    log.atInfo().log("Uploaded connector jar to GCS.");
    initialized = true;

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
      log.atInfo().log("Crated source and sink subscriptions");
    }
  }

  @After
  public void tearDown() throws Exception {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      try {
        subscriptionAdminClient.deleteSubscription(sourceSubscriptionName);
        subscriptionAdminClient.deleteSubscription(sinkSubscriptionName);
        log.atInfo().log("Deleted source and sink subscriptions");
      } catch (NotFoundException ignored) {
        // Ignore this as resources may not have been created.
      }
    }

    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      topicAdminClient.deleteTopic(sourceTopicName.toString());
      topicAdminClient.deleteTopic(sinkTopicName.toString());
      log.atInfo().log("Deleted source and sink topics");
    } catch (NotFoundException ignored) {
      // Ignore this as resources may not have been created.
    }
    System.setOut(null);
  }

  @Test
  public void endToEndTest() throws Exception {
    createInstanceFromTemplate(projectId, zone, instanceName, instanceTemplateName);

    // TODO:
    // Copy connector jar from GCS to instance
    // Update connect and connector properties on instance, both source and sink
    // SSH into instance and start up Zookeeper
    // SSH into instance and start up Kafka
    // SSH into instance and create a topic
    // Publish messages to source topic
    // Assert messages can be pulled from sink subscription
  }

  public static void createInstanceFromTemplate(
      String projectId, String zone, String instanceName, String instanceTemplateName)
      throws IOException, ExecutionException, InterruptedException, TimeoutException {

    try (InstancesClient instancesClient = InstancesClient.create()) {

      InsertInstanceRequest insertInstanceRequest =
          InsertInstanceRequest.newBuilder()
              .setProject(projectId)
              .setZone(zone)
              .setInstanceResource(Instance.newBuilder().setName(instanceName).build())
              .setSourceInstanceTemplate(instanceTemplateName)
              .build();

      Operation response =
          instancesClient.insertAsync(insertInstanceRequest).get(3, TimeUnit.MINUTES);

      if (response.hasError()) {
        System.out.println("Instance creation from template failed ! ! " + response);
        return;
      }
      System.out.printf(
          "Instance creation from template: Operation Status %s: %s ",
          instanceName, response.getStatus());
    }
  }
}
