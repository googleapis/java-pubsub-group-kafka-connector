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

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.MessageMetadata;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.ProjectPath;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.PublisherSettings;
import com.google.cloud.pubsublite.internal.Publisher;
import com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory;
import com.google.cloud.pubsublite.internal.wire.PubsubContext.Framework;
import com.google.cloud.pubsublite.internal.wire.RoutingPublisherBuilder;
import com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder;
import java.util.Map;
import org.apache.kafka.common.config.ConfigValue;

class PublisherFactoryImpl implements PublisherFactory {

  private static final Framework FRAMEWORK = Framework.of("KAFKA_CONNECT");

  private PartitionPublisherFactory getPartitionPublisherFactory(TopicPath topic) {
    return new PartitionPublisherFactory() {
      @Override
      public Publisher<MessageMetadata> newPublisher(Partition partition) throws ApiException {
        SinglePartitionPublisherBuilder.Builder singlePartitionBuilder =
            SinglePartitionPublisherBuilder.newBuilder()
                .setTopic(topic)
                .setPartition(partition)
                .setBatchingSettings(PublisherSettings.DEFAULT_BATCHING_SETTINGS);
        return singlePartitionBuilder.build();
      }

      @Override
      public void close() {}
    };
  }

  @Override
  public Publisher<MessageMetadata> newPublisher(Map<String, String> params) {
    Map<String, ConfigValue> config = ConfigDefs.config().validateAll(params);
    RoutingPublisherBuilder.Builder builder = RoutingPublisherBuilder.newBuilder();
    TopicPath topic =
        TopicPath.newBuilder()
            .setProject(
                ProjectPath.parse("projects/" + config.get(ConfigDefs.PROJECT_FLAG).value())
                    .project())
            .setLocation(CloudZone.parse(config.get(ConfigDefs.LOCATION_FLAG).value().toString()))
            .setName(TopicName.of(config.get(ConfigDefs.TOPIC_NAME_FLAG).value().toString()))
            .build();
    builder.setTopic(topic);
    builder.setPublisherFactory(getPartitionPublisherFactory(topic));
    return builder.build();
  }
}
