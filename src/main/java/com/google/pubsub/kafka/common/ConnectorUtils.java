/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.pubsub.kafka.common;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

/** Utility methods and constants that are repeated across one or more classes. */
public final class ConnectorUtils {
  private ConnectorUtils() {}

  public static final String SCHEMA_NAME = ByteString.class.getName();
  public static final String CPS_SUBSCRIPTION_FORMAT = "projects/%s/subscriptions/%s";
  public static final String CPS_PROJECT_CONFIG = "cps.project";
  public static final String CPS_TOPIC_CONFIG = "cps.topic";
  public static final String CPS_ENDPOINT = "cps.endpoint";
  public static final String CPS_DEFAULT_ENDPOINT = "pubsub.googleapis.com:443";
  public static final String CPS_USE_EMULATOR = "cps.useEmulator";
  public static final String PUBSUB_EMULATOR_HOST = "PUBSUB_EMULATOR_HOST";
  public static final String CPS_ENFORCE_OFFICIAL_ENDPOINTS = "CPS_ENFORCE_OFFICIAL_ENDPOINTS";
  public static final String CPS_MESSAGE_KEY_ATTRIBUTE = "key";
  public static final String CPS_ORDERING_KEY_ATTRIBUTE = "orderingKey";
  public static final String GCP_CREDENTIALS_FILE_PATH_CONFIG = "gcp.credentials.file.path";
  public static final String GCP_CREDENTIALS_JSON_CONFIG = "gcp.credentials.json";
  public static final String GCP_SA_CREDENTIALS_FILE_PATH_CONFIG = "gcp.sa.credentials.file.path";
  public static final String GCP_SA_CREDENTIALS_JSON_CONFIG = "gcp.sa.credentials.json";
  public static final String KAFKA_MESSAGE_CPS_BODY_FIELD = "message";
  public static final String KAFKA_TOPIC_ATTRIBUTE = "kafka.topic";
  public static final String KAFKA_PARTITION_ATTRIBUTE = "kafka.partition";
  public static final String KAFKA_OFFSET_ATTRIBUTE = "kafka.offset";
  public static final String KAFKA_TIMESTAMP_ATTRIBUTE = "kafka.timestamp";

  /**
   * Patterns matching Google Cloud Pub/Sub endpoints:
   * 1. Global: pubsub.googleapis.com
   * 2. Locational: <region>-pubsub.googleapis.com (e.g. us-central1-pubsub.googleapis.com)
   * 3. Regional: pubsub.<region>.rep.googleapis.com (e.g. pubsub.us-central1.rep.googleapis.com)
   */
  public static final Pattern GLOBAL_ENDPOINT_PATTERN =
    Pattern.compile("^pubsub\\.googleapis\\.com$");

  public static final Pattern LOCATIONAL_ENDPOINT_PATTERN =
    Pattern.compile("^[a-z]+-[a-z]+[0-9]+-pubsub\\.googleapis\\.com$");

  public static final Pattern REGIONAL_REP_ENDPOINT_PATTERN =
    Pattern.compile("^pubsub\\.[a-z]+-[a-z]+[0-9]+\\.rep\\.googleapis\\.com$");

  public static boolean isAllowedCpsHost(String host) {
    if (host == null) {
      return false;
    }
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    return GLOBAL_ENDPOINT_PATTERN.matcher(normalizedHost).matches()
        || LOCATIONAL_ENDPOINT_PATTERN.matcher(normalizedHost).matches()
        || REGIONAL_REP_ENDPOINT_PATTERN.matcher(normalizedHost).matches();
  }

  public static void validateEndpoint(String endpoint) {
    if (endpoint == null || endpoint.trim().isEmpty()) {
      throw new ConfigException(CPS_ENDPOINT, endpoint, "Endpoint cannot be null or empty.");
    }
    String trimmed = endpoint.trim();

    int colonIndex = trimmed.lastIndexOf(':');
    if (colonIndex <= 0
        || colonIndex != trimmed.indexOf(':')
        || colonIndex == trimmed.length() - 1
        || trimmed.contains("/")
        || trimmed.contains("\\")
        || trimmed.contains("?")
        || trimmed.contains("#")
        || trimmed.contains("@")) {
      throw new ConfigException(
          CPS_ENDPOINT,
          endpoint,
          "Endpoint must be in '<host>:<port>' format (e.g., 'pubsub.googleapis.com:443').");
    }

    String host = trimmed.substring(0, colonIndex);
    String portStr = trimmed.substring(colonIndex + 1);

    if (!isAllowedCpsHost(host)) {
      throw new ConfigException(
          CPS_ENDPOINT,
          endpoint,
          "Host is not an allowed Cloud Pub/Sub endpoint.");
    }

    int port;
    try {
      port = Integer.parseInt(portStr);
    } catch (NumberFormatException unused) {
      port = -1;
    }
    if (port < 1 || port > 65535) {
      throw new ConfigException(CPS_ENDPOINT, endpoint, "Port must be between 1 and 65535.");
    }
  }

  /** Validator class for {@link ConnectorUtils#CPS_ENDPOINT}. */
  public static final class CpsEndpointValidator implements ConfigDef.Validator {
    private final Supplier<String> envLookup;

    public CpsEndpointValidator() {
      this(() -> System.getenv(CPS_ENFORCE_OFFICIAL_ENDPOINTS));
    }

    CpsEndpointValidator(Supplier<String> envLookup) {
      this.envLookup = envLookup;
    }

    @Override
    public void ensureValid(String name, Object o) {
      String enforceOfficialEndpoints = envLookup.get();
      if (!Boolean.parseBoolean(enforceOfficialEndpoints)
          && !Objects.equals(enforceOfficialEndpoints, "1")) {
        return;
      }
      validateEndpoint(o == null ? null : o.toString());
    }

    @Override
    public String toString() {
      return "Official Cloud Pub/Sub endpoint in '<host>:<port>' format (e.g., 'pubsub.googleapis.com:443')";
    }
  }

  private static ScheduledExecutorService newDaemonExecutor(String prefix) {
    return Executors.newScheduledThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors() * 5),
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat(prefix + "-%d").build());
  }

  // A shared executor for Pub/Sub clients to use.
  private static Optional<ScheduledExecutorService> systemExecutor = Optional.empty();

  public static synchronized ScheduledExecutorService getSystemExecutor() {
    if (!systemExecutor.isPresent()) {
      systemExecutor = Optional.of(newDaemonExecutor("pubsub-connect-system"));
    }
    return systemExecutor.get();
  }

  // Resolve the endpoint. When using the emulator, prefer PUBSUB_EMULATOR_HOST and fall back to
  // the configured cps.endpoint.
  public static String getPubsubEndpoint(boolean useEmulator, String cpsEndpoint) {
    if (useEmulator) {
      String emulatorHost = System.getenv(PUBSUB_EMULATOR_HOST);
      if (emulatorHost != null && !emulatorHost.isEmpty()) {
        return emulatorHost;
      }
    }

    return cpsEndpoint;
  }
}
