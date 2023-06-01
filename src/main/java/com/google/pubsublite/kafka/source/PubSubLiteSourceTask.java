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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;

public class PubSubLiteSourceTask extends SourceTask {

  private final PollerFactory factory;
  private @Nullable Poller poller;

  @VisibleForTesting
  PubSubLiteSourceTask(PollerFactory factory) {
    this.factory = factory;
  }

  public PubSubLiteSourceTask() {
    this(new PollerFactoryImpl());
  }

  @Override
  public String version() {
    return new PubSubLiteSourceConnector().version();
  }

  @Override
  public void start(Map<String, String> props) {
    if (poller != null) {
      throw new IllegalStateException("Called start when poller already exists.");
    }
    poller = factory.newPoller(props);
  }

  @Override
  public @Nullable List<SourceRecord> poll() {
    return poller.poll();
  }

  @Override
  public void stop() {
    if (poller == null) {
      throw new IllegalStateException("Called stop when poller doesn't exist.");
    }
    try {
      poller.close();
    } finally {
      poller = null;
    }
  }
}
