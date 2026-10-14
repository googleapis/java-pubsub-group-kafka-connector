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
package com.google.pubsub.kafka.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.pubsub.kafka.common.ConnectorCredentialsProvider;
import com.google.pubsub.kafka.common.ConnectorUtils;
import com.google.pubsub.kafka.common.TestCredentialsProviders;
import com.google.pubsub.kafka.common.TestCredentialsProviders.FailingConstructorProvider;
import com.google.pubsub.kafka.common.TestCredentialsProviders.RecordingProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.errors.ConnectException;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CloudPubSubSourceConnector}. */
public class CloudPubSubSourceConnectorTest {

  private static final int NUM_TASKS = 10;
  private static final String CPS_PROJECT = "hello";
  private static final String CPS_SUBSCRIPTION = "big";
  private static final String KAFKA_TOPIC = "world";

  private CloudPubSubSourceConnector connector;
  private Map<String, String> props;

  @Before
  public void setup() {
    connector = spy(new CloudPubSubSourceConnector());
    props = new HashMap<>();
    props.put(CloudPubSubSourceConnector.CPS_SUBSCRIPTION_CONFIG, CPS_SUBSCRIPTION);
    props.put(ConnectorUtils.CPS_PROJECT_CONFIG, CPS_PROJECT);
    props.put(CloudPubSubSourceConnector.KAFKA_TOPIC_CONFIG, KAFKA_TOPIC);
  }

  @Test(expected = ConnectException.class)
  public void testStartWhenSubscriptionNonexistant() {
    doThrow(new ConnectException(""))
        .when(connector)
        .verifySubscription(
            anyString(),
            anyString(),
            any(ConnectorCredentialsProvider.class),
            anyString(),
            anyBoolean());
    connector.start(props);
  }

  @Test
  public void testStartClosesCredentialsProviderAndTasksGetProviderSettings() {
    doNothing()
        .when(connector)
        .verifySubscription(
            anyString(),
            anyString(),
            any(ConnectorCredentialsProvider.class),
            anyString(),
            anyBoolean());
    props.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, RecordingProvider.class.getName());
    props.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX + TestCredentialsProviders.TEST_ID,
        "sourceConnector");

    connector.start(props);

    assertTrue(RecordingProvider.forTest("sourceConnector").closed);
    for (Map<String, String> taskConfig : connector.taskConfigs(NUM_TASKS)) {
      assertEquals(
          "sourceConnector",
          taskConfig.get(
              ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX + TestCredentialsProviders.TEST_ID));
    }
  }

  @Test
  public void testStartClosesCredentialsProviderWhenVerificationFails() {
    doThrow(new ConnectException(""))
        .when(connector)
        .verifySubscription(
            anyString(),
            anyString(),
            any(ConnectorCredentialsProvider.class),
            anyString(),
            anyBoolean());
    props.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, RecordingProvider.class.getName());
    props.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX + TestCredentialsProviders.TEST_ID,
        "sourceConnectorVerifyFails");

    assertThrows(ConnectException.class, () -> connector.start(props));

    assertTrue(RecordingProvider.forTest("sourceConnectorVerifyFails").closed);
  }

  @Test
  public void testStartWithEmulatorDoesNotCreateCredentialsProvider() {
    doNothing()
        .when(connector)
        .verifySubscription(anyString(), anyString(), any(), anyString(), anyBoolean());
    props.put(ConnectorUtils.CPS_USE_EMULATOR, "true");
    props.put(ConnectorUtils.CPS_ENDPOINT, "localhost:8085");
    props.put(
        ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG,
        FailingConstructorProvider.class.getName());

    connector.start(props);
  }

  @Test(expected = ConfigException.class)
  public void testStartWhenRequiredConfigMissing() {
    connector.start(new HashMap<String, String>());
  }

  @Test
  public void testTaskConfigs() {
    doNothing()
        .when(connector)
        .verifySubscription(
            anyString(),
            anyString(),
            any(ConnectorCredentialsProvider.class),
            anyString(),
            anyBoolean());
    connector.start(props);
    List<Map<String, String>> taskConfigs = connector.taskConfigs(NUM_TASKS);
    assertEquals(taskConfigs.size(), NUM_TASKS);
    for (int i = 0; i < taskConfigs.size(); ++i) {
      assertEquals(taskConfigs.get(i), props);
    }
  }

  @Test
  public void testSourceConnectorTaskClass() {
    assertEquals(CloudPubSubSourceTask.class, connector.taskClass());
  }
}
