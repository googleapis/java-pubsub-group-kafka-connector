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

import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Singleton holder for a MeterRegistry that is fully configured programmatically via connector.
 * All settings are provided through {@link #configure(Properties)} and hardcoded defaults
 * exist only to fall back if a particular key is missing.
 *
 * Usage in Kafka Connect:
 *   Properties config = new Properties();
 *   // supply all micrometer/statsd settings here:
 *   config.put("METRICS_ENABLED", "true");
 *   config.put("STATSD_HOST", "datadog.monitoring");
 *   config.put("STATSD_PORT", "8125");
 *   config.put("STATSD_STEP", "60");
 *   config.put("STATSD_FLAVOR", "DATADOG");
 *   config.put("DATADOG_SERVICE", "my-service");
 *   config.put("DATADOG_ENV", "prod");
 *   config.put("DATADOG_TAGS", "team:olc");
 *   MetricsRegistry.configure(config);
 *   MeterRegistry registry = MetricsRegistry.get();
 */
public final class MetricsRegistry {
    private static final Logger log = LoggerFactory.getLogger(MetricsRegistry.class);
    private static final Properties configProps = new Properties();
    private static volatile MeterRegistry instance;

    private MetricsRegistry() { /* no-op */ }

    /**
     * Supply all metrics configuration programmatically. Must be called once before get().
     */
    public static synchronized void configure(Properties props) {
        configProps.clear();
        configProps.putAll(props);
        instance = null;
    }

    /**
     * @return shared MeterRegistry (StatsD or fallback SimpleMeterRegistry) built lazily
     */
    public static MeterRegistry get() {
        if (instance == null) {
            synchronized (MetricsRegistry.class) {
                if (instance == null) {
                    boolean enabled = Boolean.parseBoolean(
                            configProps.getProperty(ConnectorUtils.MICROMETER_ENABLED, "false"));
                    if (enabled) {
                        StatsdConfig statsdConfig = new StatsdConfig() {
                            @Override public String get(String key) { return configProps.getProperty(key); }
                            @Override public StatsdFlavor flavor() { return StatsdFlavor.DATADOG; }
                            @Override public String host() {
                                return configProps.getProperty(ConnectorUtils.STATSD_HOST, "datadog.monitoring");
                            }
                            @Override public int port() {
                                String p = configProps.getProperty(ConnectorUtils.STATSD_PORT);
                                return (p != null ? Integer.parseInt(p) : 8125);
                            }
                            @Override public Duration step() {
                                String s = configProps.getProperty(ConnectorUtils.STATSD_STEP);
                                long sec = (s != null ? Long.parseLong(s) : 60);
                                return Duration.ofSeconds(sec);
                            }
                        };
                        StatsdMeterRegistry statsdRegistry = new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
                        applyCommonTags(statsdRegistry);
                        instance = statsdRegistry;
                        log.info("Metrics enabled: StatsD at {}:{}", statsdConfig.host(), statsdConfig.port());
                    } else {
                        instance = new SimpleMeterRegistry();
                        log.info("Metrics disabled; using SimpleMeterRegistry fallback");
                    }
                }
            }
        }
        return instance;
    }

    /** Apply DD_SERVICE, DD_ENV, and DD_TAGS common tags **/
    private static void applyCommonTags(MeterRegistry registry) {
        List<String> tags = new ArrayList<>();
        String service = configProps.getProperty(ConnectorUtils.DATADOG_SERVICE);
        if (service != null && !service.isEmpty()) {
            tags.add("service"); tags.add(service);
        }
        String env = configProps.getProperty(ConnectorUtils.DATADOG_ENV);
        if (env != null && !env.isEmpty()) {
            tags.add("env"); tags.add(env);
        }
        String extra = configProps.getProperty(ConnectorUtils.DATADOG_TAGS);
        if (extra != null && !extra.isEmpty()) {
            for (String entry : extra.split(",")) {
                String[] kv = entry.split(":", 2);
                if (kv.length == 2) { tags.add(kv[0]); tags.add(kv[1]); }
            }
        }
        if (!tags.isEmpty()) {
            registry.config().commonTags(tags.toArray(new String[0]));
            log.info("Applied common tags: {}", tags);
        }
    }
}
