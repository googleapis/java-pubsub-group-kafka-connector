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

import com.google.pubsublite.kafka.common.Version;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

public class PubSubLiteSinkConnector extends SinkConnector {
  private Map<String, String> props;

  @Override
  public String version() {
    return Version.version();
  }

  @Override
  public void start(Map<String, String> map) {
    props = map;
  }

  @Override
  public Class<? extends Task> taskClass() {
    return PubSubLiteSinkTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(int i) {
    return Collections.nCopies(i, props);
  }

  @Override
  public void stop() {}

  @Override
  public ConfigDef config() {
    return ConfigDefs.config();
  }
}
