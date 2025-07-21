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
package com.google.pubsub.kafka.common;

import io.micrometer.core.instrument.*;

import java.util.function.Supplier;

public class PubSubSinkMetrics {

    private final Counter putRequests;
    private final Counter putFailures;
    private final Timer   putLatency;

    private final Counter flushRequests;
    private final Counter flushFailures;
    private final Timer   flushLatency;

    private final Counter startRequests;
    private final Counter startFailures;
    private final Counter stopRequests;

    public PubSubSinkMetrics(MeterRegistry registry) {
        this.putRequests = Counter.builder("cloudpubsub.extended.sink.put.requests")
                .description("Number of put(Collection<SinkRecord>) calls")
                .register(registry);
        this.putFailures = Counter.builder("cloudpubsub.extended.sink.put.failures")
                .description("Number of failures in put(...)")
                .register(registry);
        this.putLatency = Timer.builder("cloudpubsub.extended.sink.put.latency")
                .description("Latency of put(...) calls")
                .publishPercentiles(0.5, 0.95)
                .register(registry);

        this.flushRequests = Counter.builder("cloudpubsub.extended.sink.flush.requests")
                .description("Number of flush(...) calls")
                .register(registry);
        this.flushFailures = Counter.builder("cloudpubsub.extended.sink.flush.failures")
                .description("Number of failures in flush(...)")
                .register(registry);
        this.flushLatency = Timer.builder("cloudpubsub.extended.sink.flush.latency")
                .description("Latency of flush(...) calls")
                .publishPercentiles(0.5, 0.95)
                .register(registry);

        this.startRequests = Counter.builder("cloudpubsub.extended.sink.start.requests")
                .description("Number of start(...) calls")
                .register(registry);
        this.startFailures = Counter.builder("cloudpubsub.extended.sink.start.failures")
                .description("Number of failures in start(...)")
                .register(registry);

        this.stopRequests = Counter.builder("cloudpubsub.extended.sink.stop.requests")
                .description("Number of stop() calls")
                .register(registry);
    }

    public <T> T recordPut(Supplier<T> work) {
        return putLatency.record(() -> {
            putRequests.increment();
            try {
                return work.get();
            } catch (RuntimeException e) {
                putFailures.increment();
                throw e;
            }
        });
    }

    public void recordPut(Runnable work) {
        putLatency.record(() -> {
            putRequests.increment();
            try {
                work.run();
            } catch (RuntimeException e) {
                putFailures.increment();
                throw e;
            }
        });
    }

    public void recordFlush(Runnable work) {
        flushLatency.record(() -> {
            flushRequests.increment();
            try {
                work.run();
            } catch (RuntimeException e) {
                flushFailures.increment();
                throw e;
            }
        });
    }

    public <T> T recordStart(Supplier<T> work) {
        startRequests.increment();
        try {
            return work.get();
        } catch (RuntimeException e) {
            startFailures.increment();
            throw e;
        }
    }

    public void recordStop(Runnable work) {
        stopRequests.increment();
        work.run();
    }
}
