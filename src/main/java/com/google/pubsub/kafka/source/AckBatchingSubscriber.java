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
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.ReceivedMessage;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AckBatchingSubscriber implements CloudPubSubSubscriber {
  interface AlarmFactory {
    Future<?> newAlarm(Runnable runnable);
  }

  private static class IdsAndFuture {
    Collection<String> ids;
    SettableApiFuture<Empty> future;
  }

  private final CloudPubSubSubscriber underlying;

  @GuardedBy("this")
  private final Deque<IdsAndFuture> toSend = new ArrayDeque<>();

  private final Future<?> alarm;

  private final Duration finalAckWait;

  public AckBatchingSubscriber(CloudPubSubSubscriber underlying, AlarmFactory alarmFactory) {
    this(underlying, alarmFactory, Duration.ZERO);
  }

  /**
   * @param finalAckWait how long close() waits for the last ack batch before closing the underlying
   *     subscriber; used when the credentials are closed right after. Zero does not wait.
   */
  public AckBatchingSubscriber(
      CloudPubSubSubscriber underlying, AlarmFactory alarmFactory, Duration finalAckWait) {
    this.underlying = underlying;
    this.finalAckWait = finalAckWait;
    this.alarm = alarmFactory.newAlarm(this::flush);
  }

  @Override
  public ApiFuture<List<ReceivedMessage>> pull() {
    return underlying.pull();
  }

  @Override
  public synchronized ApiFuture<Empty> ackMessages(Collection<String> ackIds) {
    IdsAndFuture idsAndFuture = new IdsAndFuture();
    idsAndFuture.ids = ackIds;
    idsAndFuture.future = SettableApiFuture.create();
    toSend.add(idsAndFuture);
    return idsAndFuture.future;
  }

  private ApiFuture<Empty> flush() {
    List<String> ackIds = new ArrayList<>();
    List<SettableApiFuture<Empty>> futures = new ArrayList<>();
    synchronized (this) {
      if (toSend.isEmpty()) {
        return null;
      }
      toSend.forEach(
          pair -> {
            ackIds.addAll(pair.ids);
            futures.add(pair.future);
          });
      toSend.clear();
    }
    ApiFuture<Empty> response = underlying.ackMessages(ackIds);
    ApiFutures.addCallback(
        response,
        new ApiFutureCallback<Empty>() {
          @Override
          public void onFailure(Throwable t) {
            futures.forEach(future -> future.setException(t));
          }

          @Override
          public void onSuccess(Empty result) {
            futures.forEach(future -> future.set(result));
          }
        },
        MoreExecutors.directExecutor());
    return response;
  }

  @Override
  public void close() {
    alarm.cancel(false);
    try {
      alarm.get();
    } catch (Throwable ignored) {
    }
    ApiFuture<Empty> lastAcks = flush();
    if (lastAcks != null && !finalAckWait.isZero()) {
      try {
        lastAcks.get(finalAckWait.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
        // Failed or slow acks are redelivered by Pub/Sub.
      }
    }
    underlying.close();
  }
}
