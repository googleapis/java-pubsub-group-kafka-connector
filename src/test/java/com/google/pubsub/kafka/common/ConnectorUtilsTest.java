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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import org.apache.kafka.common.config.ConfigException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectorUtilsTest {


  @Test
  public void testValidDefaultEndpoint() {
    ConnectorUtils.validateEndpoint(ConnectorUtils.CPS_DEFAULT_ENDPOINT);
  }

  @Test
  public void testIsAllowedCpsHost() {
    assertThat(ConnectorUtils.isAllowedCpsHost("pubsub.googleapis.com")).isTrue();
    assertThat(ConnectorUtils.isAllowedCpsHost("asia-east1-pubsub.googleapis.com")).isTrue();
    assertThat(ConnectorUtils.isAllowedCpsHost("pubsub.asia-east1.rep.googleapis.com")).isTrue();

    // Uppercase / mixed case allowed (case-insensitive)
    assertThat(ConnectorUtils.isAllowedCpsHost("PUBSUB.GOOGLEAPIS.COM")).isTrue();
    assertThat(ConnectorUtils.isAllowedCpsHost("PubSub.GoogleApis.Com")).isTrue();
    assertThat(ConnectorUtils.isAllowedCpsHost("ASIA-EAST1-PUBSUB.GOOGLEAPIS.COM")).isTrue();
    assertThat(ConnectorUtils.isAllowedCpsHost("pubsub.asia-east1.REP.googleapis.com")).isTrue();

    // Hosts with ports or paths rejected by host matcher
    assertThat(ConnectorUtils.isAllowedCpsHost("pubsub.googleapis.com:443")).isFalse();
    assertThat(ConnectorUtils.isAllowedCpsHost("pubsub.googleapis.com\\foo")).isFalse();
    assertThat(ConnectorUtils.isAllowedCpsHost("evil.com")).isFalse();
    assertThat(ConnectorUtils.isAllowedCpsHost("169.254.169.254")).isFalse();
    assertThat(ConnectorUtils.isAllowedCpsHost(null)).isFalse();
  }

  @Test
  public void testValidGlobalEndpoints() {
    ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.googleapis.com:80");
    ConnectorUtils.validateEndpoint("  pubsub.googleapis.com:443  ");
  }

  @Test
  public void testValidLocationalEndpoints() {
    ConnectorUtils.validateEndpoint("asia-east1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("us-central1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("europe-west1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("northamerica-northeast1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("southamerica-east1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("australia-southeast1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("me-central1-pubsub.googleapis.com:443");
    ConnectorUtils.validateEndpoint("africa-south1-pubsub.googleapis.com:443");
  }

  @Test
  public void testValidRegionalEndpoints() {
    ConnectorUtils.validateEndpoint("pubsub.asia-east1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.us-central1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.europe-west1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.northamerica-northeast1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.southamerica-east1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.australia-southeast1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.me-central1.rep.googleapis.com:443");
    ConnectorUtils.validateEndpoint("pubsub.africa-south1.rep.googleapis.com:443");
  }

  @Test
  public void testValidEndpoints_caseInsensitive() {
    ConnectorUtils.validateEndpoint("PUBSUB.GOOGLEAPIS.COM:443");
    ConnectorUtils.validateEndpoint("PubSub.GoogleApis.Com:443");
    ConnectorUtils.validateEndpoint("ASIA-EAST1-PUBSUB.GOOGLEAPIS.COM:443");
    ConnectorUtils.validateEndpoint("Asia-East1-Pubsub.Googleapis.com:443");
    ConnectorUtils.validateEndpoint("PUBSUB.ASIA-EAST1.REP.GOOGLEAPIS.COM:443");
    ConnectorUtils.validateEndpoint("pubsub.Asia-East1.rep.googleapis.com:443");
  }

  @Test
  public void testInvalidEndpoints_nullOrEmpty() {
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint(null));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint(""));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("   "));
  }

  @Test
  public void testInvalidEndpoints_missingPort() {
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("asia-east1-pubsub.googleapis.com"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.asia-east1.rep.googleapis.com"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:"));
  }

  @Test
  public void testInvalidEndpoints_ssrfMetadataAndIps() {
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("169.254.169.254"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("169.254.169.254:80"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("http://169.254.169.254/computeMetadata/v1/"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("http://169.254.169.254:80"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("127.0.0.1"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("127.0.0.1:8080"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("10.0.0.1:443"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("192.168.1.1:443"));
  }

  @Test
  public void testInvalidEndpoints_localhostAndArbitraryDomains() {
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("localhost"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("localhost:8080"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("evil.com:443"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("attacker.com:443"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.attacker.com:443"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("googleapis.com:443"));
    assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint("google.com:443"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("storage.googleapis.com:443"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("compute.googleapis.com:443"));
  }

  @Test
  public void testInvalidEndpoints_subdomainSpoofing() {
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com.evil.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("PUBSUB.GOOGLEAPIS.COM.EVIL.COM:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("asia-east1-pubsub.googleapis.com.attacker.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.asia-east1.rep.googleapis.com.evil.com:443"));
  }

  @Test
  public void testInvalidEndpoints_malformedRegionNames() {
    // Missing region structure (no hyphen/numbers)
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("foo-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("evil-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.evil.rep.googleapis.com:443"));

    // Numbers before hyphen / pure numeric
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("123-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("123-456-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.123.rep.googleapis.com:443"));

    // Missing trailing region number
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("us-central-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.us-central.rep.googleapis.com:443"));

    // Multiple hyphens in region name
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("a-b-c-pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.a-b-c1.rep.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("us-central-1-pubsub.googleapis.com:443"));
  }

  @Test
  public void testInvalidEndpoints_userInfo() {
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("user:pass@pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443@evil.com"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("user@pubsub.googleapis.com:443"));
  }

  @Test
  public void testInvalidEndpoints_pathsQueryFragment() {
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443/"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443\\"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443/v1/projects"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443\\v1\\projects"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443/foo"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443\\foo"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com\\foo:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443?query=1"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443#frag"));
  }

  @Test
  public void testInvalidEndpoints_schemes() {
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("https://pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("https://pubsub.googleapis.com"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("http://pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("http://pubsub.googleapis.com"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("//pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("\\\\pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("ftp://pubsub.googleapis.com:443"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("file:///etc/passwd"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("javascript:alert(1)"));
  }

  @Test
  public void testInvalidEndpoints_ports() {
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:0"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:65536"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:-5"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:abc"));
    assertThrows(
        ConfigException.class,
        () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:443:443"));
    assertThrows(
        ConfigException.class, () -> ConnectorUtils.validateEndpoint(":443"));
  }

  @Test
  public void testCpsEndpointValidatorToString() {
    ConnectorUtils.CpsEndpointValidator validator = new ConnectorUtils.CpsEndpointValidator();
    assertThat(validator.toString())
        .isEqualTo(
            "Official Cloud Pub/Sub endpoint in '<host>:<port>' format (e.g.,"
                + " 'pubsub.googleapis.com:443')");
  }

  @Test
  public void testCpsEndpointValidator_disabledWhenUnset() {
    ConnectorUtils.CpsEndpointValidator validator =
        new ConnectorUtils.CpsEndpointValidator(() -> null);

    // Any endpoint is allowed when enforcement is unset / disabled
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "pubsub.googleapis.com:443");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "evil.com:443");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "169.254.169.254:80");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "not-an-endpoint");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, null);
  }

  @Test
  public void testCpsEndpointValidator_disabledWhenFalseOrOtherValues() {
    for (String disabledVal : Arrays.asList("false", "FALSE", "0", "random", "")) {
      ConnectorUtils.CpsEndpointValidator validator =
          new ConnectorUtils.CpsEndpointValidator(() -> disabledVal);

      validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085");
      validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "evil.com:443");
      validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, null);
    }
  }

  @Test
  public void testCpsEndpointValidator_enabledWithTrue() {
    ConnectorUtils.CpsEndpointValidator validator =
        new ConnectorUtils.CpsEndpointValidator(() -> "true");

    // Official endpoints pass
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "pubsub.googleapis.com:443");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "us-central1-pubsub.googleapis.com:443");
    validator.ensureValid(
        ConnectorUtils.CPS_ENDPOINT, "pubsub.us-central1.rep.googleapis.com:443");
    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "PUBSUB.GOOGLEAPIS.COM:443");

    // Unofficial or invalid endpoints fail
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085"));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "evil.com:443"));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "169.254.169.254:80"));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "pubsub.googleapis.com"));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, null));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, ""));
  }

  @Test
  public void testCpsEndpointValidator_enabledWithOne() {
    ConnectorUtils.CpsEndpointValidator validator =
        new ConnectorUtils.CpsEndpointValidator(() -> "1");

    validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "pubsub.googleapis.com:443");
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085"));
    assertThrows(
        ConfigException.class,
        () -> validator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "evil.com:443"));
  }

  @Test
  public void testCpsEndpointValidator_defaultConstructorWithSystemProperty() {
    String originalProp = System.getProperty(ConnectorUtils.CPS_ENFORCE_OFFICIAL_ENDPOINTS);
    try {
      System.setProperty(ConnectorUtils.CPS_ENFORCE_OFFICIAL_ENDPOINTS, "false");
      ConnectorUtils.CpsEndpointValidator disabledValidator =
          new ConnectorUtils.CpsEndpointValidator();
      disabledValidator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085");

      System.setProperty(ConnectorUtils.CPS_ENFORCE_OFFICIAL_ENDPOINTS, "true");
      ConnectorUtils.CpsEndpointValidator enabledValidator =
          new ConnectorUtils.CpsEndpointValidator();
      enabledValidator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "pubsub.googleapis.com:443");
      assertThrows(
          ConfigException.class,
          () -> enabledValidator.ensureValid(ConnectorUtils.CPS_ENDPOINT, "localhost:8085"));
    } finally {
      if (originalProp != null) {
        System.setProperty(ConnectorUtils.CPS_ENFORCE_OFFICIAL_ENDPOINTS, originalProp);
      } else {
        System.clearProperty(ConnectorUtils.CPS_ENFORCE_OFFICIAL_ENDPOINTS);
      }
    }
  }

  @Test
  public void testErrorMessages() {
    ConfigException nullEx =
        assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint(null));
    assertThat(nullEx).hasMessageThat().contains("Endpoint cannot be null or empty.");

    ConfigException emptyEx =
        assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint(""));
    assertThat(emptyEx).hasMessageThat().contains("Endpoint cannot be null or empty.");

    ConfigException noPortEx =
        assertThrows(
            ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com"));
    assertThat(noPortEx)
        .hasMessageThat()
        .contains(
            "Endpoint must be in '<host>:<port>' format (e.g., 'pubsub.googleapis.com:443').");

    ConfigException colonPrefixEx =
        assertThrows(ConfigException.class, () -> ConnectorUtils.validateEndpoint(":443"));
    assertThat(colonPrefixEx)
        .hasMessageThat()
        .contains(
            "Endpoint must be in '<host>:<port>' format (e.g., 'pubsub.googleapis.com:443').");

    ConfigException backslashEx =
        assertThrows(
            ConfigException.class,
            () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com\\evil:443"));
    assertThat(backslashEx)
        .hasMessageThat()
        .contains(
            "Endpoint must be in '<host>:<port>' format (e.g., 'pubsub.googleapis.com:443').");

    ConfigException hostEx =
        assertThrows(
            ConfigException.class, () -> ConnectorUtils.validateEndpoint("evil.com:443"));
    assertThat(hostEx)
        .hasMessageThat()
        .endsWith(": Host is not an allowed Cloud Pub/Sub endpoint.");
    assertThat(hostEx).hasMessageThat().doesNotContain("Host 'evil.com'");

    ConfigException portEx =
        assertThrows(
            ConfigException.class, () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:0"));
    assertThat(portEx).hasMessageThat().contains("Port must be between 1 and 65535.");

    ConfigException portNotNumberEx =
        assertThrows(
            ConfigException.class,
            () -> ConnectorUtils.validateEndpoint("pubsub.googleapis.com:invalid"));
    assertThat(portNotNumberEx).hasMessageThat().contains("Port must be between 1 and 65535.");
  }
}
