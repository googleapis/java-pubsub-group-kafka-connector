/*
 * Copyright 2023 Google LLC
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

import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.internal.CheckedApiException;
import com.google.cloud.pubsublite.internal.RoutingPolicy;
import com.google.cloud.pubsublite.proto.PubSubMessage;

/** A routing policy that extracts the original kafka partition and routes to that partition. */
class KafkaPartitionRoutingPolicy implements RoutingPolicy {
  private final long numPartitions;

  KafkaPartitionRoutingPolicy(long numPartitions) {
    this.numPartitions = numPartitions;
  }

  @Override
  public Partition route(PubSubMessage message) throws CheckedApiException {
    Partition partition = getPartition(message);
    if (partition.value() >= numPartitions) {
      throw new CheckedApiException(
          "Kafka topic has more partitions than Pub/Sub Lite topic. OrderingMode.KAFKA cannot be used.",
          Code.FAILED_PRECONDITION);
    }
    return partition;
  }

  private Partition getPartition(PubSubMessage message) throws CheckedApiException {
    try {
      return Partition.of(
          Long.parseLong(
              message
                  .getAttributesOrThrow(Constants.KAFKA_PARTITION_HEADER)
                  .getValues(0)
                  .toStringUtf8()));
    } catch (Throwable t) {
      throw toCanonical(t);
    }
  }
}
