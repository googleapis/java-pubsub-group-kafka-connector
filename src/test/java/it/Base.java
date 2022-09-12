package it;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.compute.v1.AccessConfig;
import com.google.cloud.compute.v1.AccessConfig.NetworkTier;
import com.google.cloud.compute.v1.AttachedDisk;
import com.google.cloud.compute.v1.AttachedDiskInitializeParams;
import com.google.cloud.compute.v1.GetInstanceRequest;
import com.google.cloud.compute.v1.InsertInstanceRequest;
import com.google.cloud.compute.v1.InsertInstanceTemplateRequest;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstanceProperties;
import com.google.cloud.compute.v1.InstanceTemplate;
import com.google.cloud.compute.v1.InstanceTemplatesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.Items;
import com.google.cloud.compute.v1.Metadata;
import com.google.cloud.compute.v1.NetworkInterface;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.compute.v1.ServiceAccount;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.flogger.GoogleLogger;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.utils.cli.CommandLineException;

public class Base {

  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();

  private static final String bucketName = System.getenv("BUCKET_NAME");
  protected static final String runId = UUID.randomUUID().toString().substring(0, 8);
  protected static String mavenHome;
  protected static String workingDir;
  protected static String connectorVersion;
  protected static String startupScriptName;
  protected static String connectorJarName;
  protected static String connectorJarNameInGCS;
  protected static String cpsConnectorJarLoc;
  protected static String testResourcesDirLoc;
  protected static String cpsSinkConnectorPropertiesName;
  protected static String cpsSinkConnectorPropertiesGCSName;
  protected static String cpsSourceConnectorPropertiesName;
  protected static String cpsSourceConnectorPropertiesGCSName;
  protected static String pslSinkConnectorPropertiesName;
  protected static String pslSinkConnectorPropertiesGCSName;
  protected static String pslSourceConnectorPropertiesName;
  protected static String pslSourceConnectorPropertiesGCSName;
  protected static String kafkaVersion;
  protected static String scalaVersion;
  protected static final String region = "us-central1";
  protected static final Character zone = 'b';
  protected static final String location = region + "-" + String.valueOf(zone);

  protected static void findMavenHome() throws Exception {
    Process p = Runtime.getRuntime().exec("mvn --version");
    BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
    assertThat(p.waitFor()).isEqualTo(0);
    String s;
    while ((s = stdOut.readLine()) != null) {
      if (StringUtils.startsWith(s, "Maven home: ")) {
        mavenHome = s.replace("Maven home: ", "");
      }
    }
  }

  private static void runMavenCommand(
      String workingDir, Optional<InvocationOutputHandler> outputHandler, String... goals)
      throws MavenInvocationException, CommandLineException {
    InvocationRequest request = new DefaultInvocationRequest();
    request.setPomFile(new File(workingDir + "/pom.xml"));
    request.setGoals(Arrays.asList(goals.clone()));
    Invoker invoker = new DefaultInvoker();
    outputHandler.ifPresent(invoker::setOutputHandler);
    invoker.setMavenHome(new File(mavenHome));
    InvocationResult result = invoker.execute(request);
    if (result.getExecutionException() != null) {
      throw result.getExecutionException();
    }
    assertThat(result.getExitCode()).isEqualTo(0);
  }

  protected static void mavenPackage(String workingDir)
      throws MavenInvocationException, CommandLineException {
    runMavenCommand(workingDir, Optional.empty(), "clean", "package", "-DskipTests=true");
  }

  private static void getVersion(String workingDir, InvocationOutputHandler outputHandler)
      throws MavenInvocationException, CommandLineException {
    runMavenCommand(
        workingDir,
        Optional.of(outputHandler),
        "-q",
        "-Dexec.executable=echo",
        "-Dexec.args='${project.version}'",
        "--non-recursive",
        "exec:exec");
  }

  protected static void setupVersions() throws MavenInvocationException, CommandLineException {
    workingDir = System.getProperty("user.dir");
    getVersion(workingDir, (l) -> connectorVersion = l);
    log.atInfo().log("Connector version is: %s", connectorVersion);

    startupScriptName = "kafka_vm_startup_script.sh";
    connectorJarName = String.format("pubsub-group-kafka-connector-%s.jar", connectorVersion);
    connectorJarNameInGCS =
        String.format("pubsub-group-kafka-connector-%s-%s.jar", connectorVersion, runId);
    cpsConnectorJarLoc = String.format("%s/target/%s", workingDir, connectorJarName);

    testResourcesDirLoc = String.format("%s/src/test/resources/", workingDir);
    cpsSinkConnectorPropertiesName = "cps-sink-connector-test.properties";
    cpsSinkConnectorPropertiesGCSName =
        cpsSinkConnectorPropertiesName.replace(".properties", runId + ".properties");
    cpsSourceConnectorPropertiesName = "cps-source-connector-test.properties";
    cpsSourceConnectorPropertiesGCSName =
        cpsSourceConnectorPropertiesName.replace(".properties", runId + ".properties");
    pslSinkConnectorPropertiesName = "pubsub-lite-sink-connector-test.properties";
    pslSinkConnectorPropertiesGCSName =
        pslSinkConnectorPropertiesName.replace(".properties", runId + ".properties");
    pslSourceConnectorPropertiesName = "pubsub-lite-source-connector-test.properties";
    pslSourceConnectorPropertiesGCSName =
        pslSourceConnectorPropertiesName.replace(".properties", runId + ".properties");

    // TODO: Get Kafka and Scala versions programmatically: {major}.{minor}.{patch}.
    kafkaVersion = "3.2.0";
    scalaVersion = "2.13";
  }

  protected static void uploadGCS(Storage storage, String fileNameInGCS, String fileLoc)
      throws Exception {
    BlobId blobId = BlobId.of(bucketName, fileNameInGCS);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, Files.readAllBytes(Paths.get(fileLoc)));
  }

  protected static void createInstanceTemplate(
      String projectId, String projectNumber, String instanceTemplateName)
      throws IOException, ExecutionException, InterruptedException, TimeoutException {
    try (InstanceTemplatesClient instanceTemplatesClient = InstanceTemplatesClient.create()) {

      String machineType = "e2-medium";
      String sourceImage = "projects/debian-cloud/global/images/family/debian-11";

      // The template describes the size and source image of the boot disk
      // to attach to the instance.
      AttachedDisk attachedDisk =
          AttachedDisk.newBuilder()
              .setInitializeParams(
                  AttachedDiskInitializeParams.newBuilder()
                      .setSourceImage(sourceImage)
                      .setDiskSizeGb(10)
                      .build())
              .setAutoDelete(true)
              .setBoot(true)
              .build();

      // The template connects the instance to the `default` network,
      // without specifying a subnetwork.
      NetworkInterface networkInterface =
          NetworkInterface.newBuilder()
              .setName("global/networks/default")
              // The template lets the instance use an external IP address.
              .addAccessConfigs(
                  AccessConfig.newBuilder()
                      .setName("External NAT")
                      .setType(AccessConfig.Type.ONE_TO_ONE_NAT.toString())
                      .setNetworkTier(NetworkTier.PREMIUM.toString())
                      .build())
              .build();

      Metadata metadata =
          Metadata.newBuilder()
              .addItems(
                  Items.newBuilder()
                      .setKey("serial-port-logging-enable")
                      .setValue(String.valueOf(true))
                      .build())
              .addItems(Items.newBuilder().setKey("project_id").setValue(projectId).build())
              .addItems(Items.newBuilder().setKey("run_id").setValue(runId).build())
              .addItems(Items.newBuilder().setKey("kafka_version").setValue(kafkaVersion).build())
              .addItems(
                  Items.newBuilder()
                      .setKey("startup-script")
                      .setValue(
                          new String(
                              Files.readAllBytes(
                                  Paths.get(testResourcesDirLoc + startupScriptName)),
                              StandardCharsets.UTF_8))
                      .build())
              .addItems(Items.newBuilder().setKey("scala_version").setValue(scalaVersion).build())
              .addItems(Items.newBuilder().setKey("gcs_bucket").setValue(bucketName).build())
              .addItems(
                  Items.newBuilder()
                      .setKey("cps_connector_jar_name")
                      .setValue(connectorJarNameInGCS)
                      .build())
              .addItems(
                  Items.newBuilder()
                      .setKey("cps_sink_connector_properties_name")
                      .setValue(cpsSinkConnectorPropertiesGCSName)
                      .build())
              .addItems(
                  Items.newBuilder()
                      .setKey("cps_source_connector_properties_name")
                      .setValue(cpsSourceConnectorPropertiesGCSName)
                      .build())
              .addItems(Items.newBuilder().setKey("psl_zone").setValue(location).build())
              .addItems(
                  Items.newBuilder()
                      .setKey("psl_sink_connector_properties_name")
                      .setValue(pslSinkConnectorPropertiesGCSName)
                      .build())
              .addItems(
                  Items.newBuilder()
                      .setKey("psl_source_connector_properties_name")
                      .setValue(pslSourceConnectorPropertiesGCSName)
                      .build())
              .build();

      InstanceProperties instanceProperties =
          InstanceProperties.newBuilder()
              .addDisks(attachedDisk)
              .setMachineType(machineType)
              .addNetworkInterfaces(networkInterface)
              .addServiceAccounts(
                  ServiceAccount.newBuilder()
                      .setEmail(
                          String.format("%s-compute@developer.gserviceaccount.com", projectNumber))
                      .addAllScopes(
                          Arrays.asList(
                              "https://www.googleapis.com/auth/cloud-platform",
                              "https://www.googleapis.com/auth/pubsub",
                              "https://www.googleapis.com/auth/devstorage.read_write"))
                      .build())
              .setMetadata(metadata)
              .build();

      InsertInstanceTemplateRequest insertInstanceTemplateRequest =
          InsertInstanceTemplateRequest.newBuilder()
              .setProject(projectId)
              .setInstanceTemplateResource(
                  InstanceTemplate.newBuilder()
                      .setName(instanceTemplateName)
                      .setProperties(instanceProperties)
                      .build())
              .build();

      // Create the Instance Template.
      Operation response =
          instanceTemplatesClient
              .insertAsync(insertInstanceTemplateRequest)
              .get(3, TimeUnit.MINUTES);

      TimeUnit.MINUTES.sleep(3);

      if (response.hasError()) {
        System.out.println("\nInstance Template creation failed ! ! " + response);
        return;
      }
      System.out.printf(
          "\nInstance Template Operation Status %s: %s",
          instanceTemplateName, response.getStatus());
    }
  }

  protected static void createInstanceFromTemplate(
      String projectId, String zone, String instanceName, String instanceTemplateName)
      throws IOException, ExecutionException, InterruptedException, TimeoutException {

    try (InstancesClient instancesClient = InstancesClient.create()) {

      InsertInstanceRequest insertInstanceRequest =
          InsertInstanceRequest.newBuilder()
              .setProject(projectId)
              .setZone(zone)
              .setInstanceResource(Instance.newBuilder().setName(instanceName).build())
              .setSourceInstanceTemplate(
                  String.format(
                      "projects/%s/global/instanceTemplates/%s", projectId, instanceTemplateName))
              .build();

      Operation response =
          instancesClient.insertAsync(insertInstanceRequest).get(3, TimeUnit.MINUTES);

      TimeUnit.MINUTES.sleep(3);

      if (response.hasError()) {
        System.out.println("\nInstance creation from template failed ! ! " + response);
        return;
      }
      System.out.printf(
          "\nInstance creation from template: Operation Status %s: %s ",
          instanceName, response.getStatus());
    }
  }

  protected static Instance getInstance(String projectId, String zone, String instanceName)
      throws IOException {
    try (InstancesClient instancesClient = InstancesClient.create()) {
      GetInstanceRequest getInstanceRequest =
          GetInstanceRequest.newBuilder()
              .setProject(projectId)
              .setZone(zone)
              .setInstance(instanceName)
              .build();
      Instance instance = instancesClient.get(getInstanceRequest);
      return instance;
    }
  }
}
