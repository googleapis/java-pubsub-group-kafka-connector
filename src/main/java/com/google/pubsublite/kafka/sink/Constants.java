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

public final class Constants {

  private Constants() {}

  public static final String KAFKA_TOPIC_HEADER = "x-goog-pubsublite-source-kafka-topic";
  public static final String KAFKA_PARTITION_HEADER = "x-goog-pubsublite-source-kafka-partition";
  public static final String KAFKA_OFFSET_HEADER = "x-goog-pubsublite-source-kafka-offset";
  public static final String KAFKA_EVENT_TIME_TYPE_HEADER =
      "x-goog-pubsublite-source-kafka-event-time-type";
  public static final String PUBSUBLITE_KAFKA_SINK_CONNECTOR_NAME =
      "JAVA_PUBSUBLITE_KAFKA_SINK_CONNECTOR";
}
