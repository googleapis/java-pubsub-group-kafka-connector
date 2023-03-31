/*
 * Copyright 2020 Google LLC
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
package com.google.pubsublite.kafka.sink;

import com.google.pubsub.kafka.common.ConnectorUtils;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;

final class ConfigDefs {

  private ConfigDefs() {}

  static final String PROJECT_FLAG = "pubsublite.project";
  static final String LOCATION_FLAG = "pubsublite.location";
  static final String TOPIC_NAME_FLAG = "pubsublite.topic";
  static final String ORDERING_MODE_FLAG = "pubsublite.ordering.mode";

  static ConfigDef config() {
    return new ConfigDef()
        .define(
            PROJECT_FLAG,
            ConfigDef.Type.STRING,
            Importance.HIGH,
            "The project containing the topic to which to publish.")
        .define(
            LOCATION_FLAG,
            ConfigDef.Type.STRING,
            Importance.HIGH,
            "The cloud zone (like europe-south7-q) containing the topic to which to publish.")
        .define(
            TOPIC_NAME_FLAG,
            ConfigDef.Type.STRING,
            Importance.HIGH,
            "The name of the topic to which to publish.")
        .define(
            ORDERING_MODE_FLAG,
            ConfigDef.Type.STRING,
            OrderingMode.DEFAULT.name(),
            Importance.HIGH,
            "The ordering mode to use for publishing to Pub/Sub Lite. If set to `KAFKA`, messages will be republished to the same partition index they were read from on the source topic. Note that this means the Pub/Sub Lite topic *must* have the same number of partitions as the source Kafka topic.")
        .define(
            ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG,
            ConfigDef.Type.STRING,
            "",
            Importance.HIGH,
            "The path to the GCP credentials file")
        .define(
            ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG,
            ConfigDef.Type.STRING,
            "",
            Importance.HIGH,
            "GCP JSON credentials");
  }
}
