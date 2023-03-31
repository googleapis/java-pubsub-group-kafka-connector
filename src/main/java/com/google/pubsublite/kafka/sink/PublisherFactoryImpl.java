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

import static com.google.cloud.pubsublite.internal.ExtractStatus.toCanonical;
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.addDefaultSettings;
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.getCallContext;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.CloudRegionOrZone;
import com.google.cloud.pubsublite.MessageMetadata;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.ProjectPath;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.PublisherSettings;
import com.google.cloud.pubsublite.internal.Publisher;
import com.google.cloud.pubsublite.internal.wire.PartitionCountWatchingPublisherSettings;
import com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory;
import com.google.cloud.pubsublite.internal.wire.PubsubContext;
import com.google.cloud.pubsublite.internal.wire.PubsubContext.Framework;
import com.google.cloud.pubsublite.internal.wire.RoutingMetadata;
import com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder;
import com.google.cloud.pubsublite.v1.AdminServiceClient;
import com.google.cloud.pubsublite.v1.AdminServiceSettings;
import com.google.cloud.pubsublite.v1.PublisherServiceClient;
import com.google.cloud.pubsublite.v1.PublisherServiceSettings;
import com.google.pubsub.kafka.common.ConnectorCredentialsProvider;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

class PublisherFactoryImpl implements PublisherFactory {

  private static final Framework FRAMEWORK = Framework.of("KAFKA_CONNECT");

  private PartitionPublisherFactory getPartitionPublisherFactory(
      TopicPath topic, ConnectorCredentialsProvider credentialsProvider) {

    return new PartitionPublisherFactory() {
      private Optional<PublisherServiceClient> publisherServiceClient = Optional.empty();

      private synchronized PublisherServiceClient getServiceClient() throws ApiException {
        if (publisherServiceClient.isPresent()) return publisherServiceClient.get();
        try {
          publisherServiceClient =
              Optional.of(
                  PublisherServiceClient.create(
                      addDefaultSettings(
                          topic.location().extractRegion(),
                          PublisherServiceSettings.newBuilder()
                              .setCredentialsProvider(credentialsProvider))));
          return publisherServiceClient.get();
        } catch (Throwable t) {
          throw toCanonical(t).underlying;
        }
      }

      @Override
      public Publisher<MessageMetadata> newPublisher(Partition partition) throws ApiException {
        PublisherServiceClient client = getServiceClient();
        SinglePartitionPublisherBuilder.Builder singlePartitionBuilder =
            SinglePartitionPublisherBuilder.newBuilder()
                .setTopic(topic)
                .setPartition(partition)
                .setBatchingSettings(PublisherSettings.DEFAULT_BATCHING_SETTINGS)
                .setStreamFactory(
                    responseStream -> {
                      ApiCallContext context =
                          getCallContext(
                              PubsubContext.of(FRAMEWORK), RoutingMetadata.of(topic, partition));
                      return client.publishCallable().splitCall(responseStream, context);
                    });
        return singlePartitionBuilder.build();
      }

      @Override
      public void close() {}
    };
  }

  @Override
  public Publisher<MessageMetadata> newPublisher(Map<String, String> params) {
    Map<String, Object> config = ConfigDefs.config().parse(params);
    ConnectorCredentialsProvider credentialsProvider =
        ConnectorCredentialsProvider.fromConfig(config);
    CloudRegionOrZone location =
        CloudRegionOrZone.parse(config.get(ConfigDefs.LOCATION_FLAG).toString());
    PartitionCountWatchingPublisherSettings.Builder builder =
        PartitionCountWatchingPublisherSettings.newBuilder();
    TopicPath topic =
        TopicPath.newBuilder()
            .setProject(
                ProjectPath.parse("projects/" + config.get(ConfigDefs.PROJECT_FLAG)).project())
            .setLocation(location)
            .setName(TopicName.of(config.get(ConfigDefs.TOPIC_NAME_FLAG).toString()))
            .build();
    builder.setTopic(topic);
    builder.setPublisherFactory(getPartitionPublisherFactory(topic, credentialsProvider));
    try {
      builder.setAdminClient(
          AdminClient.create(
              AdminClientSettings.newBuilder()
                  .setRegion(location.extractRegion())
                  .setServiceClient(
                      AdminServiceClient.create(
                          addDefaultSettings(
                              location.extractRegion(),
                              AdminServiceSettings.newBuilder()
                                  .setCredentialsProvider(credentialsProvider))))
                  .build()));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    if (OrderingMode.valueOf(config.get(ConfigDefs.ORDERING_MODE_FLAG).toString())
        == OrderingMode.KAFKA) {
      builder.setRoutingPolicyFactory(KafkaPartitionRoutingPolicy::new);
    }
    return builder.build().instantiate();
  }
}
