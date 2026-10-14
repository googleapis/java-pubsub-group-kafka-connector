/*
 * Copyright 2026 Google LLC
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

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.common.Configurable;

/**
 * {@link CredentialsProvider} implementations for testing {@code gcp.credentials.provider.class}.
 */
public final class TestCredentialsProviders {
  /** Provider setting that names the test, so each test can find the instance it created. */
  public static final String TEST_ID = "test.id";

  private TestCredentialsProviders() {}

  /**
   * Records how it was configured and whether it was closed. Instances register themselves under
   * their {@link #TEST_ID} setting.
   */
  public static class RecordingProvider
      implements CredentialsProvider, Configurable, AutoCloseable {
    public static final GoogleCredentials CREDENTIALS =
        GoogleCredentials.create(new AccessToken("fake-token", null));
    private static final Map<String, RecordingProvider> INSTANCES = new ConcurrentHashMap<>();

    /**
     * Provider setting that makes getCredentials() fail, as an unreachable identity source would.
     */
    public static final String FAIL_CREDENTIALS = "fail.credentials";

    public volatile Map<String, ?> config;
    public volatile boolean closed;
    public final AtomicInteger credentialCalls = new AtomicInteger();

    public static RecordingProvider forTest(String testId) {
      return INSTANCES.get(testId);
    }

    @Override
    public void configure(Map<String, ?> configs) {
      config = configs;
      Object testId = configs.get(TEST_ID);
      if (testId != null) {
        INSTANCES.put(testId.toString(), this);
      }
    }

    @Override
    public Credentials getCredentials() throws IOException {
      credentialCalls.incrementAndGet();
      if (config != null && "true".equals(config.get(FAIL_CREDENTIALS))) {
        throw new IOException("no credentials");
      }
      return CREDENTIALS;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** Fails in {@code configure} with an Error, as a missing dependency would. */
  public static class ErrorInConfigureProvider extends RecordingProvider {
    @Override
    public void configure(Map<String, ?> configs) {
      super.configure(configs);
      throw new NoClassDefFoundError("com/example/Missing");
    }
  }

  /** Returns one long-lived unscoped credential, as a caching provider does. */
  public static class StableUnscopedProvider implements CredentialsProvider {
    private final ScopeRequiringCredentials credentials =
        new ScopeRequiringCredentials(Collections.emptyList());

    @Override
    public Credentials getCredentials() {
      return credentials;
    }
  }

  /** Rejects its settings in {@code configure}, after it may already hold resources. */
  public static class RejectingProvider extends RecordingProvider {
    @Override
    public void configure(Map<String, ?> configs) {
      super.configure(configs);
      throw new IllegalArgumentException("bad setting");
    }
  }

  /** Neither {@link Configurable} nor {@link AutoCloseable}. */
  public static class PlainProvider implements CredentialsProvider {
    @Override
    public Credentials getCredentials() {
      return RecordingProvider.CREDENTIALS;
    }
  }

  /** Returns credentials that still need a scope, as unscoped user or impersonated ones do. */
  public static class UnscopedProvider implements CredentialsProvider {
    @Override
    public Credentials getCredentials() {
      return new ScopeRequiringCredentials(Collections.emptyList());
    }
  }

  /** Fails in its constructor, as a provider missing its environment would. */
  public static class FailingConstructorProvider implements CredentialsProvider {
    public FailingConstructorProvider() {
      throw new IllegalStateException("missing PROVIDER_HOME");
    }

    @Override
    public Credentials getCredentials() {
      return RecordingProvider.CREDENTIALS;
    }
  }

  /** Fails while its class is initialized, as one with a missing dependency would. */
  public static class FailingStaticInitProvider implements CredentialsProvider {
    static {
      fail();
    }

    private static void fail() {
      throw new IllegalStateException("static init failed");
    }

    @Override
    public Credentials getCredentials() {
      return RecordingProvider.CREDENTIALS;
    }
  }

  /** {@link GoogleCredentials} that report whether a scope was added. */
  public static class ScopeRequiringCredentials extends GoogleCredentials {
    public final Collection<String> scopes;

    ScopeRequiringCredentials(Collection<String> scopes) {
      this.scopes = scopes;
    }

    @Override
    public boolean createScopedRequired() {
      return scopes.isEmpty();
    }

    @Override
    public GoogleCredentials createScoped(Collection<String> newScopes) {
      return new ScopeRequiringCredentials(newScopes);
    }
  }
}
