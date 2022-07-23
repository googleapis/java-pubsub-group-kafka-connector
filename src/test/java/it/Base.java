package it;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.flogger.GoogleLogger;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
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

  // TODO: use `System.getenv("BUCKET_NAME");`
  private static final String bucketName = "pubsublite-it";
  protected final String runId = UUID.randomUUID().toString();
  protected String mavenHome;
  protected String workingDir;
  protected String connectorVersion;
  protected String connectorJarName;
  protected String connectorJarNameInGCS;
  protected String connectorJarLoc;

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
  }

  protected void uploadGCS(Storage storage, String fileNameInGCS, String fileLoc) throws Exception {
    BlobId blobId = BlobId.of(bucketName, fileNameInGCS);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, Files.readAllBytes(Paths.get(fileLoc)));
  }
}
