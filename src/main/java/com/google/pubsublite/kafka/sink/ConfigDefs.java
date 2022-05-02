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

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;

final class ConfigDefs {

  private ConfigDefs() {
  }

  static final String PROJECT_FLAG = "pubsublite.project";
  static final String LOCATION_FLAG = "pubsublite.location";
  static final String TOPIC_NAME_FLAG = "pubsublite.topic";

  static ConfigDef config() {
    return new ConfigDef()
        .define(PROJECT_FLAG, ConfigDef.Type.STRING, Importance.HIGH,
            "The project containing the topic to which to publish.")
        .define(LOCATION_FLAG, ConfigDef.Type.STRING, Importance.HIGH,
            "The cloud zone (like europe-south7-q) containing the topic to which to publish.")
        .define(TOPIC_NAME_FLAG, ConfigDef.Type.STRING, Importance.HIGH,
            "The name of the topic to which to publish.");
  }
}
