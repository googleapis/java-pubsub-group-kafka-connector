/*
 * Copyright 2026 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.pubsub.kafka.common.TestCredentialsProviders.FailingConstructorProvider;
import com.google.pubsub.kafka.common.TestCredentialsProviders.FailingStaticInitProvider;
import com.google.pubsub.kafka.common.TestCredentialsProviders.PlainProvider;
import com.google.pubsub.kafka.common.TestCredentialsProviders.RecordingProvider;
import com.google.pubsub.kafka.common.TestCredentialsProviders.ScopeRequiringCredentials;
import com.google.pubsub.kafka.common.TestCredentialsProviders.UnscopedProvider;
import com.google.pubsub.kafka.sink.CloudPubSubSinkConnector;
import com.google.pubsub.kafka.source.CloudPubSubSourceConnector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.ConfigValue;
import org.junit.Test;

public class ConnectorCredentialsProviderTest {

  private static Map<String, Object> validated(Object providerClass) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConnectorUtils.GCP_SA_CREDENTIALS_FILE_PATH_CONFIG, "");
    config.put(ConnectorUtils.GCP_SA_CREDENTIALS_JSON_CONFIG, "");
    config.put(ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG, "");
    config.put(ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG, "");
    config.put(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, providerClass);
    return config;
  }

  private static Map<String, String> originals(String testId) {
    Map<String, String> originals = new HashMap<>();
    originals.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX + TestCredentialsProviders.TEST_ID, testId);
    return originals;
  }

  @Test
  public void usesConfiguredProviderClass() throws Exception {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(
            validated(RecordingProvider.class), originals("usesConfiguredProviderClass"));

    assertThat(provider.getCredentials()).isSameInstanceAs(RecordingProvider.CREDENTIALS);
  }

  @Test
  public void acceptsProviderClassName() throws Exception {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(
            validated(PlainProvider.class.getName()), new HashMap<>());

    assertThat(provider.getCredentials()).isSameInstanceAs(RecordingProvider.CREDENTIALS);
  }

  @Test
  public void passesOnlyPrefixedOriginalsToProviderWithPrefixStripped() {
    Map<String, String> originals = originals("passesOnlyPrefixedOriginals");
    originals.put("gcp.credentials.provider.foo", "bar");
    originals.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, RecordingProvider.class.getName());
    originals.put(ConnectorUtils.CPS_PROJECT_CONFIG, "my-project");

    ConnectorCredentialsProvider.fromConfig(validated(RecordingProvider.class), originals);

    assertThat(RecordingProvider.forTest("passesOnlyPrefixedOriginals").config)
        .containsExactly(
            TestCredentialsProviders.TEST_ID, "passesOnlyPrefixedOriginals", "foo", "bar");
  }

  @Test
  public void supportsProviderThatIsNotConfigurable() throws Exception {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(validated(PlainProvider.class), originals("x"));

    assertThat(provider.getCredentials()).isSameInstanceAs(RecordingProvider.CREDENTIALS);
  }

  @Test
  public void addsCloudPlatformScopeWhenCredentialsRequireOne() throws Exception {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(validated(UnscopedProvider.class), new HashMap<>());

    assertThat(((ScopeRequiringCredentials) provider.getCredentials()).scopes)
        .containsExactly("https://www.googleapis.com/auth/cloud-platform");
  }

  @Test
  public void rejectsProviderClassCombinedWithAnyOtherCredentialSetting() {
    for (String other :
        Arrays.asList(
            ConnectorUtils.GCP_SA_CREDENTIALS_FILE_PATH_CONFIG,
            ConnectorUtils.GCP_SA_CREDENTIALS_JSON_CONFIG,
            ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG,
            ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG)) {
      Map<String, Object> config = validated(PlainProvider.class);
      config.put(other, "value");

      ConfigException e =
          assertThrows(
              other,
              ConfigException.class,
              () -> ConnectorCredentialsProvider.fromConfig(config, new HashMap<>()));
      assertWithMessage(other).that(e).hasMessageThat().contains(other);
    }
  }

  @Test
  public void rejectsProviderClassThatIsNotACredentialsProvider() {
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () ->
                ConnectorCredentialsProvider.fromConfig(validated(String.class), new HashMap<>()));

    assertThat(e).hasMessageThat().contains("com.google.api.gax.core.CredentialsProvider");
  }

  @Test
  public void reportsWhyTheProviderConstructorFailed() {
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () ->
                ConnectorCredentialsProvider.fromConfig(
                    validated(FailingConstructorProvider.class), new HashMap<>()));

    assertThat(e).hasMessageThat().contains("missing PROVIDER_HOME");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void reportsProviderClassThatFailsToInitialize() {
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () ->
                ConnectorCredentialsProvider.fromConfig(
                    validated(FailingStaticInitProvider.class), new HashMap<>()));

    assertThat(e).hasMessageThat().contains(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG);
    assertThat(e).hasCauseThat().isInstanceOf(LinkageError.class);
  }

  @Test
  public void closeClosesTheProvider() {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(
            validated(RecordingProvider.class), originals("closeClosesTheProvider"));

    provider.close();

    assertThat(RecordingProvider.forTest("closeClosesTheProvider").closed).isTrue();
  }

  @Test
  public void closesTheProviderWhenConfigureFails() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ConnectorCredentialsProvider.fromConfig(
                validated(TestCredentialsProviders.RejectingProvider.class),
                originals("closesTheProviderWhenConfigureFails")));

    assertThat(RecordingProvider.forTest("closesTheProviderWhenConfigureFails").closed).isTrue();
  }

  @Test
  public void closeIsANoOpForBuiltInCredentials() {
    Map<String, Object> config = validated(null);

    ConnectorCredentialsProvider.fromConfig(config, new HashMap<>()).close();
  }

  @Test
  public void configOnlyOverloadRejectsProviderClass() {
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> ConnectorCredentialsProvider.fromConfig(validated(PlainProvider.class)));

    assertThat(e).hasMessageThat().contains(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG);
  }

  private static Map<String, String> connectorProps(String providerClass) {
    Map<String, String> props = new HashMap<>();
    props.put(ConnectorUtils.CPS_PROJECT_CONFIG, "my-project");
    props.put(ConnectorUtils.CPS_TOPIC_CONFIG, "my-topic");
    props.put(CloudPubSubSourceConnector.CPS_SUBSCRIPTION_CONFIG, "my-subscription");
    props.put(CloudPubSubSourceConnector.KAFKA_TOPIC_CONFIG, "my-kafka-topic");
    props.put(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, providerClass);
    return props;
  }

  @Test
  public void connectorsRejectAClassThatIsNotACredentialsProviderWhenValidating() {
    Map<String, String> props = connectorProps(String.class.getName());

    for (ConfigDef configDef :
        Arrays.asList(
            new CloudPubSubSourceConnector().config(), new CloudPubSubSinkConnector().config())) {
      assertThat(
              configDef
                  .validateAll(props)
                  .get(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG)
                  .errorMessages())
          .isNotEmpty();
    }
  }

  // Validating the config must not run the provider's static initializer; a failing one is then
  // reported as a ConfigException for the setting, not as a raw Error from ConfigDef.
  @Test
  public void providerWhoseStaticInitializerFailsIsAConfigErrorFromAParsedConfig() {
    Map<String, String> props = connectorProps(FailingStaticInitProvider.class.getName());
    Map<String, Object> parsed = new CloudPubSubSinkConnector().config().parse(props);

    ConfigException e =
        assertThrows(
            ConfigException.class, () -> ConnectorCredentialsProvider.fromConfig(parsed, props));
    assertThat(e).hasCauseThat().isInstanceOf(LinkageError.class);
  }

  @Test
  public void connectorsReportConflictingCredentialSettingsWhenValidating() {
    Map<String, String> props = connectorProps(PlainProvider.class.getName());
    props.put(ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG, "{}");

    for (org.apache.kafka.connect.connector.Connector connector :
        Arrays.asList(new CloudPubSubSourceConnector(), new CloudPubSubSinkConnector())) {
      ConfigValue value =
          connector.validate(props).configValues().stream()
              .filter(v -> v.name().equals(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG))
              .findFirst()
              .get();
      assertWithMessage(connector.getClass().getSimpleName())
          .that(value.errorMessages())
          .isNotEmpty();
    }
  }

  @Test
  public void closesTheProviderWhenConfigureThrowsAnError() {
    assertThrows(
        NoClassDefFoundError.class,
        () ->
            ConnectorCredentialsProvider.fromConfig(
                validated(TestCredentialsProviders.ErrorInConfigureProvider.class),
                originals("closesWhenConfigureThrowsAnError")));

    assertThat(RecordingProvider.forTest("closesWhenConfigureThrowsAnError").closed).isTrue();
  }

  @Test
  public void reusesTheScopedCredentialsForTheSameProviderCredentials() throws Exception {
    ConnectorCredentialsProvider provider =
        ConnectorCredentialsProvider.fromConfig(
            validated(TestCredentialsProviders.StableUnscopedProvider.class), new HashMap<>());

    assertThat(provider.getCredentials()).isSameInstanceAs(provider.getCredentials());
  }

  @Test
  public void connectorsRejectUnknownProviderClassWhenValidating() {
    Map<String, String> props = new HashMap<>();
    props.put(ConnectorUtils.CPS_PROJECT_CONFIG, "my-project");
    props.put(ConnectorUtils.CPS_TOPIC_CONFIG, "my-topic");
    props.put(CloudPubSubSourceConnector.CPS_SUBSCRIPTION_CONFIG, "my-subscription");
    props.put(CloudPubSubSourceConnector.KAFKA_TOPIC_CONFIG, "my-kafka-topic");
    props.put(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, "com.example.DoesNotExist");

    for (ConfigDef configDef :
        Arrays.asList(
            new CloudPubSubSourceConnector().config(), new CloudPubSubSinkConnector().config())) {
      assertThat(
              configDef
                  .validateAll(props)
                  .get(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG)
                  .errorMessages())
          .isNotEmpty();
    }
  }
}
