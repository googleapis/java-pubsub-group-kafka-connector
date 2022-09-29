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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class PubSubLiteSourceTaskTest {

  @Mock PollerFactory factory;
  @Mock Poller poller;
  PubSubLiteSourceTask task;

  @Before
  public void setUp() {
    initMocks(this);
    when(factory.newPoller(any())).thenReturn(poller);
    task = new PubSubLiteSourceTask(factory);
    task.start(ImmutableMap.of());
    verify(factory).newPoller(ImmutableMap.of());
    assertThrows(IllegalStateException.class, () -> task.start(ImmutableMap.of()));
  }

  @Test
  public void poll() {
    when(poller.poll()).thenReturn(ImmutableList.of());
    assertThat(task.poll()).isEmpty();
  }

  @Test
  public void stop() {
    task.stop();
    verify(poller).close();
    assertThrows(IllegalStateException.class, () -> task.stop());
  }
}
