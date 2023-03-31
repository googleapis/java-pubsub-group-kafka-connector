/*
 * Copyright 2018 Google Inc.
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

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ConnectorCredentialsProvider implements CredentialsProvider {
  private static final List<String> GCP_SCOPE =
      Arrays.asList("https://www.googleapis.com/auth/cloud-platform");

  CredentialsProvider impl;

  private ConnectorCredentialsProvider(CredentialsProvider impl) {
    this.impl = impl;
  }

  public static ConnectorCredentialsProvider fromConfig(Map<String, Object> config) {
    String credentialsPath = config.get(ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG).toString();
    String credentialsJson = config.get(ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG).toString();
    if (!credentialsPath.isEmpty()) {
      if (!credentialsJson.isEmpty()) {
        throw new IllegalArgumentException(
            "May not set both "
                + ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG
                + " and "
                + ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG);
      }
      return ConnectorCredentialsProvider.fromFile(credentialsPath);
    } else if (!credentialsJson.isEmpty()) {
      return ConnectorCredentialsProvider.fromJson(credentialsJson);
    } else {
      return ConnectorCredentialsProvider.fromDefault();
    }
  }

  public static ConnectorCredentialsProvider fromFile(String credentialPath) {
    return new ConnectorCredentialsProvider(
        () ->
            GoogleCredentials.fromStream(new FileInputStream(credentialPath))
                .createScoped(GCP_SCOPE));
  }

  public static ConnectorCredentialsProvider fromJson(String credentialsJson) {
    return new ConnectorCredentialsProvider(
        () ->
            GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes()))
                .createScoped(GCP_SCOPE));
  }

  public static ConnectorCredentialsProvider fromDefault() {
    return new ConnectorCredentialsProvider(
        () -> GoogleCredentials.getApplicationDefault().createScoped(GCP_SCOPE));
  }

  @Override
  public Credentials getCredentials() throws IOException {
    return impl.getCredentials();
  }
}
