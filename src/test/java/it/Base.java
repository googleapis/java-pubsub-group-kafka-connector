package it;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.compute.v1.AccessConfig;
import com.google.cloud.compute.v1.AccessConfig.NetworkTier;
import com.google.cloud.compute.v1.AttachedDisk;
import com.google.cloud.compute.v1.AttachedDiskInitializeParams;
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

  // TODO: System.getenv("BUCKET_NAME")
  private static final String bucketName = "pubsublite-it";
  protected static final String runId = UUID.randomUUID().toString().substring(0, 8);
  protected String mavenHome;
  protected String workingDir;
  protected String connectorVersion;
  protected String connectorJarName;
  protected String connectorJarNameInGCS;
  protected String connectorJarLoc;
  protected String kafkaVersion;
  protected String scalaVersion;

  protected void findMavenHome() throws Exception {
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

  private void runMavenCommand(
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

  protected void mavenPackage(String workingDir)
      throws MavenInvocationException, CommandLineException {
    runMavenCommand(workingDir, Optional.empty(), "clean", "package", "-DskipTests=true");
  }

  private void getVersion(String workingDir, InvocationOutputHandler outputHandler)
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

  protected void setupVersions() throws MavenInvocationException, CommandLineException {
    workingDir = System.getProperty("user.dir");
    getVersion(workingDir, (l) -> connectorVersion = l);
    log.atInfo().log("Connector version is: %s", connectorVersion);

    connectorJarName = String.format("pubsub-group-kafka-connector-%s.jar", connectorVersion);
    connectorJarNameInGCS =
        String.format("pubsub-group-kafka-connector-%s-%s.jar", connectorVersion, runId);
    connectorJarLoc = String.format("%s/target/%s", workingDir, connectorJarName);

    // TODO: Get Kafka and Scala versions programmatically: {major}.{minor}.{patch}.
    kafkaVersion = "3.2.0";
    scalaVersion = "2.13";
  }

  protected void uploadGCS(Storage storage, String fileNameInGCS, String fileLoc) throws Exception {
    BlobId blobId = BlobId.of(bucketName, fileNameInGCS);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, Files.readAllBytes(Paths.get(fileLoc)));
  }

  protected void createInstanceTemplate(
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

      // TODO: flesh out the complete startup script. Possibly link to a file.
      Metadata metadata =
          Metadata.newBuilder()
              .addItems(
                  Items.newBuilder()
                      .setKey("url")
                      .setValue(
                          String.format(
                              "https://downloads.apache.org/kafka/%s/kafka_%s-%s.tgz",
                              kafkaVersion, scalaVersion, kafkaVersion))
                      .build())
              .addItems(
                  Items.newBuilder()
                      .setKey("startup-script")
                      .setValue(
                          "#! /bin/bash\nURL=$(curl http://metadata.google.internal/computeMetadata/v1/instance/attributes/url -H \"Metadata-Flavor: Google\")\nsudo apt-get update\nsudo apt-get install -yq wget openjdk-11-jdk maven\nwget $URL")
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
                      .addScopes("https://www.googleapis.com/auth/pubsub")
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

  protected void createInstanceFromTemplate(
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
}
