package it;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.compute.v1.InstanceTemplatesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.flogger.GoogleLogger;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
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
  // TODO: System.getenv("GOOGLE_CLOUD_PROJECT_NUMBER");
  private static final String projectNumber = "779844219229";
  private static final String sourceTopicId = "source-topic-" + runId;
  private static final String sourceSubscriptionId = "source-subscription-" + runId;
  private static final String sinkTopicId = "sink-topic-" + runId;
  private static final String sinkSubscriptionId = "sink-subscription-" + runId;
  private static final String zone = "us-central1-b";
  private static final String instanceName = "kafka-it-" + runId;
  private static final String instanceTemplateName = "kafka-it-template-" + runId;
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

    createInstanceTemplate(projectId, projectNumber, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance template.");

    createInstanceFromTemplate(projectId, zone, instanceName, instanceTemplateName);
    log.atInfo().log("Created Compute Engine instance from instance template");
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
  public void endToEndTest() throws Exception {
    // TODO: Implement the rest.
    // Publish messages to source topic
    // Assert messages can be pulled from sink subscription
  }
}
