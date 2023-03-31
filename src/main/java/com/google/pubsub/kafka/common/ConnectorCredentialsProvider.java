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

  GoogleCredentials credentials;

  private ConnectorCredentialsProvider(GoogleCredentials credentials) {
    this.credentials = credentials.createScoped(GCP_SCOPE);
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
    } else if (credentialsJson != null) {
      return ConnectorCredentialsProvider.fromJson(credentialsJson);
    } else {
      return ConnectorCredentialsProvider.fromDefault();
    }
  }

  public static ConnectorCredentialsProvider fromFile(String credentialPath) {
    try {
      return new ConnectorCredentialsProvider(
          GoogleCredentials.fromStream(new FileInputStream(credentialPath)));
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load credentials.", e);
    }
  }

  public static ConnectorCredentialsProvider fromJson(String credentialsJson) {
    try {
      return new ConnectorCredentialsProvider(
          GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes())));
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load credentials.", e);
    }
  }

  public static ConnectorCredentialsProvider fromDefault() {
    try {
      return new ConnectorCredentialsProvider(GoogleCredentials.getApplicationDefault());
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load credentials.", e);
    }
  }

  @Override
  public Credentials getCredentials() {
    return credentials;
  }
}
