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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.pubsublite.internal.CheckedApiException;
import com.google.cloud.pubsublite.internal.ExtractStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.Empty;
import com.google.pubsub.kafka.source.AckBatchingSubscriber.AlarmFactory;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AckBatchingSubscriberTest {
  private final AlarmFactory alarmFactory = mock(AlarmFactory.class);
  private final CloudPubSubSubscriber underlying = mock(CloudPubSubSubscriber.class);
  private Runnable onAlarm;
  private CloudPubSubSubscriber subscriber;

  @Before
  public void setUp() {
    when(alarmFactory.newAlarm(any()))
        .thenAnswer(
            args -> {
              onAlarm = args.getArgument(0);
              return Futures.immediateVoidFuture();
            });
    subscriber = new AckBatchingSubscriber(underlying, alarmFactory);
    assertThat(onAlarm).isNotNull();
  }

  @Test
  public void pullProxies() {
    subscriber.pull();
    verify(underlying, times(1)).pull();
    verifyNoMoreInteractions(underlying);
  }

  @Test
  public void closeProxies() {
    subscriber.close();
    verify(underlying, times(1)).close();
    verifyNoMoreInteractions(underlying);
  }

  public static void assertFutureThrowsCode(Future<?> f, Code code) {
    ExecutionException exception = assertThrows(ExecutionException.class, f::get);
    assertThrowableMatches(exception.getCause(), code);
  }

  public static void assertThrowableMatches(Throwable t, Code code) {
    Optional<CheckedApiException> statusOr = ExtractStatus.extract(t);
    assertThat(statusOr.get().code()).isEqualTo(code);
  }

  @Test
  public void partialFlushFailure() {
    ApiFuture<Empty> future1 = subscriber.ackMessages(ImmutableList.of("a", "b"));
    ApiFuture<Empty> future2 = subscriber.ackMessages(ImmutableList.of("c"));
    SettableApiFuture<Empty> batchDone = SettableApiFuture.create();
    when(underlying.ackMessages(ImmutableList.of("a", "b", "c"))).thenReturn(batchDone);
    onAlarm.run();
    ApiFuture<Empty> future3 = subscriber.ackMessages(ImmutableList.of("d"));
    assertThat(future1.isDone()).isFalse();
    assertThat(future2.isDone()).isFalse();
    assertThat(future3.isDone()).isFalse();
    batchDone.setException(new CheckedApiException(Code.INTERNAL).underlying);
    assertFutureThrowsCode(future1, Code.INTERNAL);
    assertFutureThrowsCode(future2, Code.INTERNAL);
    assertThat(future3.isDone()).isFalse();
  }

  @Test
  public void flushOnClose() throws Exception {
    ApiFuture<Empty> future1 = subscriber.ackMessages(ImmutableList.of("a", "b"));
    ApiFuture<Empty> future2 = subscriber.ackMessages(ImmutableList.of("c"));
    SettableApiFuture<Empty> batchDone = SettableApiFuture.create();
    when(underlying.ackMessages(ImmutableList.of("a", "b", "c"))).thenReturn(batchDone);
    subscriber.close();
    verify(underlying).ackMessages(any());
    verify(underlying).close();
    assertThat(future1.isDone()).isFalse();
    assertThat(future2.isDone()).isFalse();
    batchDone.set(null);
    future1.get();
    future2.get();
  }
}
