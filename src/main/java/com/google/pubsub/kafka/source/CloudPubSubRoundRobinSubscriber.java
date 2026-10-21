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
package com.google.pubsub.kafka.source;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ReceivedMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@link CloudPubSubSubscriber} that distributes a single subscription in round-robin fashion
 * over a set of {@link CloudPubSubGRPCSubscriber}s.
 */
public class CloudPubSubRoundRobinSubscriber implements CloudPubSubSubscriber {

  private final List<CloudPubSubGRPCSubscriber> subscribers;
  private final Duration shutdownWait;
  private int currentSubscriberIndex = 0;

  public CloudPubSubRoundRobinSubscriber(
      int subscriberCount,
      CredentialsProvider gcpCredentialsProvider,
      String endpoint,
      ProjectSubscriptionName subscriptionName,
      int cpsMaxBatchSize,
      boolean useEmulator) {
    this(
        subscriberCount,
        gcpCredentialsProvider,
        endpoint,
        subscriptionName,
        cpsMaxBatchSize,
        useEmulator,
        Duration.ZERO);
  }

  /**
   * @param shutdownWait how long close() waits, in total, for the closed stubs to terminate; used
   *     when the credentials are closed right after. Zero does not wait.
   */
  public CloudPubSubRoundRobinSubscriber(
      int subscriberCount,
      CredentialsProvider gcpCredentialsProvider,
      String endpoint,
      ProjectSubscriptionName subscriptionName,
      int cpsMaxBatchSize,
      boolean useEmulator,
      Duration shutdownWait) {
    this.shutdownWait = shutdownWait;
    subscribers = new ArrayList<>();
    try {
      for (int i = 0; i < subscriberCount; ++i) {
        subscribers.add(
            new CloudPubSubGRPCSubscriber(
                gcpCredentialsProvider, endpoint, subscriptionName, cpsMaxBatchSize, useEmulator));
      }
    } catch (RuntimeException | Error e) {
      // Do not leak the stubs already built; keep the original error.
      for (CloudPubSubGRPCSubscriber built : subscribers) {
        try {
          built.close();
        } catch (RuntimeException closeFailure) {
          e.addSuppressed(closeFailure);
        }
      }
      throw e;
    }
  }

  @Override
  public void close() {
    for (CloudPubSubGRPCSubscriber subscriber : subscribers) {
      subscriber.close();
    }
    if (shutdownWait.isZero()) {
      return;
    }
    long deadline = System.nanoTime() + shutdownWait.toNanos();
    try {
      for (CloudPubSubGRPCSubscriber subscriber : subscribers) {
        subscriber.awaitTermination(deadline);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public ApiFuture<List<ReceivedMessage>> pull() {
    currentSubscriberIndex = (currentSubscriberIndex + 1) % subscribers.size();
    return subscribers.get(currentSubscriberIndex).pull();
  }

  @Override
  public ApiFuture<Empty> ackMessages(Collection<String> ackIds) {
    currentSubscriberIndex = (currentSubscriberIndex + 1) % subscribers.size();
    return subscribers.get(currentSubscriberIndex).ackMessages(ackIds);
  }
}
