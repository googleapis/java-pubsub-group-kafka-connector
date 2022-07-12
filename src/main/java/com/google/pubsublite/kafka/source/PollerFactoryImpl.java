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
package com.google.pubsublite.kafka.source;

import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectPath;
import com.google.cloud.pubsublite.SubscriptionName;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.kafka.ConsumerSettings;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.config.ConfigValue;

class PollerFactoryImpl implements PollerFactory {

  @Override
  public Poller newPoller(Map<String, String> params) {
    Map<String, ConfigValue> config = ConfigDefs.config().validateAll(params);
    SubscriptionPath path =
        SubscriptionPath.newBuilder()
            .setProject(
                ProjectPath.parse("projects/" + config.get(ConfigDefs.PROJECT_FLAG).value())
                    .project())
            .setLocation(CloudZone.parse(config.get(ConfigDefs.LOCATION_FLAG).value().toString()))
            .setName(
                SubscriptionName.of(
                    config.get(ConfigDefs.SUBSCRIPTION_NAME_FLAG).value().toString()))
            .build();
    FlowControlSettings flowControlSettings =
        FlowControlSettings.builder()
            .setMessagesOutstanding(
                (Long) config.get(ConfigDefs.FLOW_CONTROL_PARTITION_MESSAGES_FLAG).value())
            .setBytesOutstanding(
                (Long) config.get(ConfigDefs.FLOW_CONTROL_PARTITION_BYTES_FLAG).value())
            .build();
    Consumer<byte[], byte[]> consumer =
        ConsumerSettings.newBuilder()
            .setAutocommit(true)
            .setSubscriptionPath(path)
            .setPerPartitionFlowControlSettings(flowControlSettings)
            .build()
            .instantiate();
    // There is only one topic for Pub/Sub Lite subscriptions, and the consumer only exposes this
    // topic.
    consumer.subscribe(consumer.listTopics().keySet());
    return new PollerImpl(config.get(ConfigDefs.KAFKA_TOPIC_FLAG).value().toString(), consumer);
  }
}
