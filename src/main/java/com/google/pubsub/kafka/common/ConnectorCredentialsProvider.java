/*
 * Copyright 2018 Google Inc.
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
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.ConfigValue;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies the GCP credentials for Pub/Sub clients, from a key, Application Default Credentials, or
 * a provider class named by {@code gcp.credentials.provider.class}. Close it once the clients that
 * use it are shut down.
 */
public class ConnectorCredentialsProvider implements CredentialsProvider, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ConnectorCredentialsProvider.class);
  private static final List<String> GCP_SCOPE =
      Arrays.asList("https://www.googleapis.com/auth/cloud-platform");

  CredentialsProvider impl;
  private final AutoCloseable resource;

  private ConnectorCredentialsProvider(CredentialsProvider impl) {
    this(impl, null);
  }

  private ConnectorCredentialsProvider(CredentialsProvider impl, AutoCloseable resource) {
    this.impl = impl;
    this.resource = resource;
  }

  /**
   * Builds the provider from the key and Application Default Credentials settings only.
   *
   * @param config the validated connector config
   * @return the credentials provider to use for Pub/Sub clients
   * @throws ConfigException if {@code gcp.credentials.provider.class} is set, since its settings
   *     are only available through {@link #fromConfig(Map, Map)}
   */
  public static ConnectorCredentialsProvider fromConfig(Map<String, Object> config) {
    if (providerClass(config) != null) {
      throw new ConfigException(
          ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG
              + " needs the connector's original properties; use fromConfig(config, originals)");
    }
    return fromCredentialsConfig(config);
  }

  /**
   * Like {@link #fromConfig(Map)}, but also honors {@code gcp.credentials.provider.class}. That
   * class is instantiated with its no-arg constructor and, if it implements {@link Configurable},
   * configured with every original property starting with {@code gcp.credentials.provider.} (prefix
   * stripped). {@code originals} are the raw connector properties, since validated configs drop
   * keys the connector does not declare. If the class implements {@link AutoCloseable}, {@link
   * #close()} closes it.
   *
   * @param config the validated connector config
   * @param originals the raw connector properties
   * @return the credentials provider to use for Pub/Sub clients
   */
  public static ConnectorCredentialsProvider fromConfig(
      Map<String, Object> config, Map<String, String> originals) {
    Class<?> providerClass = providerClass(config);
    if (providerClass == null) {
      return fromCredentialsConfig(config);
    }
    for (String other : OTHER_CREDENTIAL_SETTINGS) {
      if (!config.get(other).toString().isEmpty()) {
        throw new ConfigException(
            "May not set both "
                + ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG
                + " and "
                + other);
      }
    }
    return fromProviderClass(providerClass, originals);
  }

  private static final List<String> OTHER_CREDENTIAL_SETTINGS =
      Arrays.asList(
          ConnectorUtils.GCP_SA_CREDENTIALS_FILE_PATH_CONFIG,
          ConnectorUtils.GCP_SA_CREDENTIALS_JSON_CONFIG,
          ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG,
          ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG);

  /**
   * Checks {@code gcp.credentials.provider.class} when the connector config is validated: the class
   * exists and implements {@link CredentialsProvider}. It is loaded without being initialized, so a
   * provider whose static initializer fails is reported when the provider is created, as a {@link
   * ConfigException}, rather than as an Error from config parsing.
   */
  public static final ConfigDef.Validator PROVIDER_CLASS_VALIDATOR =
      (name, value) -> {
        if (value != null && !value.toString().trim().isEmpty()) {
          loadProviderClass(value.toString());
        }
      };

  /**
   * Reports a provider class combined with another credential setting on the provider class
   * setting, so the conflict shows up when the connector config is validated.
   *
   * @param props the raw connector properties
   * @param config the result of validating them, to add errors to
   */
  public static void validateCredentialSettings(Map<String, String> props, Config config) {
    String providerClass = props.get(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG);
    if (providerClass == null || providerClass.trim().isEmpty()) {
      return;
    }
    for (String other : OTHER_CREDENTIAL_SETTINGS) {
      String value = props.get(other);
      if (value != null && !value.isEmpty()) {
        for (ConfigValue configValue : config.configValues()) {
          if (configValue.name().equals(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG)) {
            configValue.addErrorMessage(
                "May not set both "
                    + ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG
                    + " and "
                    + other);
          }
        }
      }
    }
  }

  /** The configured provider class, or null when none is set. Accepts a class or a class name. */
  private static Class<?> providerClass(Map<String, Object> config) {
    Object value = config.get(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG);
    if (value == null || value.toString().trim().isEmpty()) {
      return null;
    }
    return value instanceof Class ? (Class<?>) value : loadProviderClass(value.toString());
  }

  /** Loads the provider class without initializing it, as Kafka Connect's class loader sees it. */
  private static Class<?> loadProviderClass(String name) {
    Class<?> clazz;
    try {
      clazz = Class.forName(name.trim(), false, Utils.getContextOrKafkaClassLoader());
    } catch (ClassNotFoundException e) {
      throw configError(name, "class not found", e);
    } catch (LinkageError e) {
      // The class exists but cannot be linked, e.g. a newer class version or a missing dependency.
      throw configError(name, "could not be loaded: " + e, e);
    }
    requireCredentialsProvider(clazz);
    return clazz;
  }

  private static void requireCredentialsProvider(Class<?> clazz) {
    if (!CredentialsProvider.class.isAssignableFrom(clazz)) {
      throw new ConfigException(
          ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG,
          clazz.getName(),
          "must implement " + CredentialsProvider.class.getName());
    }
  }

  private static ConfigException configError(String name, String message, Throwable cause) {
    ConfigException e =
        new ConfigException(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG, name, message);
    e.initCause(cause);
    return e;
  }

  private static ConnectorCredentialsProvider fromProviderClass(
      Class<?> providerClass, Map<String, String> originals) {
    requireCredentialsProvider(providerClass);
    CredentialsProvider provider;
    try {
      provider = (CredentialsProvider) providerClass.getDeclaredConstructor().newInstance();
    } catch (InvocationTargetException e) {
      throw instantiationFailure(providerClass, e.getCause());
    } catch (ReflectiveOperationException | LinkageError e) {
      throw instantiationFailure(providerClass, e);
    }
    ConnectorCredentialsProvider result =
        new ConnectorCredentialsProvider(
            new ScopingProvider(provider),
            provider instanceof AutoCloseable ? (AutoCloseable) provider : null);
    if (provider instanceof Configurable) {
      Map<String, String> providerConfig = new HashMap<>();
      for (Map.Entry<String, String> e : originals.entrySet()) {
        if (e.getKey().startsWith(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX)
            && !e.getKey().equals(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG)) {
          providerConfig.put(
              e.getKey().substring(ConnectorUtils.GCP_CREDENTIALS_PROVIDER_PREFIX.length()),
              e.getValue());
        }
      }
      try {
        ((Configurable) provider).configure(providerConfig);
      } catch (RuntimeException | Error e) {
        // Release anything the provider acquired before rejecting its settings.
        result.close();
        throw e;
      }
    }
    return result;
  }

  private static ConfigException instantiationFailure(Class<?> providerClass, Throwable cause) {
    ConfigException e =
        new ConfigException(
            ConnectorUtils.GCP_CREDENTIALS_PROVIDER_CLASS_CONFIG,
            providerClass.getName(),
            "could not be instantiated: " + cause);
    e.initCause(cause);
    return e;
  }

  /**
   * Adds the scope Pub/Sub needs, as the other credential sources here do, unless already set. The
   * scoped copy is kept while the provider keeps returning the same credentials, so its token cache
   * is reused instead of being rebuilt for every client.
   */
  private static final class ScopingProvider implements CredentialsProvider {
    private final CredentialsProvider provider;
    private Credentials lastProvided;
    private Credentials lastScoped;

    ScopingProvider(CredentialsProvider provider) {
      this.provider = provider;
    }

    @Override
    public Credentials getCredentials() throws IOException {
      // The provider may block (e.g. a token exchange), so it is called outside the lock. If it
      // rotates credentials while two callers race, the cache may briefly hold the older ones;
      // they are still valid, and the cost is one extra scoped copy.
      Credentials credentials = provider.getCredentials();
      synchronized (this) {
        if (credentials != lastProvided) {
          lastProvided = credentials;
          lastScoped =
              credentials instanceof GoogleCredentials
                      && ((GoogleCredentials) credentials).createScopedRequired()
                  ? ((GoogleCredentials) credentials).createScoped(GCP_SCOPE)
                  : credentials;
        }
        return lastScoped;
      }
    }
  }

  private static ConnectorCredentialsProvider fromCredentialsConfig(Map<String, Object> config) {
    // If both the `GCP_SA_CREDENTIALS_*` and `GCP_CREDENTIALS_FILE_*` properties are set,
    // give preference to the `GCP_SA_CREDENTIALS_*` variants.
    String credentialsSAPath =
        config.get(ConnectorUtils.GCP_SA_CREDENTIALS_FILE_PATH_CONFIG).toString();
    String credentialsSAJson = config.get(ConnectorUtils.GCP_SA_CREDENTIALS_JSON_CONFIG).toString();

    if (!credentialsSAPath.isEmpty()) {
      return ConnectorCredentialsProvider.getServiceAccountFromFile(credentialsSAPath);
    } else if (!credentialsSAJson.isEmpty()) {
      return ConnectorCredentialsProvider.getServiceAccountFromJson(credentialsSAJson);
    }

    String credentialsPath = config.get(ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG).toString();
    String credentialsJson = config.get(ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG).toString();
    if (!credentialsPath.isEmpty()) {
      if (!credentialsJson.isEmpty()) {
        throw new IllegalArgumentException(
            "May not set both "
                + ConnectorUtils.GCP_CREDENTIALS_FILE_PATH_CONFIG
                + " and "
                + ConnectorUtils.GCP_CREDENTIALS_JSON_CONFIG);
      }
      return ConnectorCredentialsProvider.fromFile(credentialsPath);
    } else if (!credentialsJson.isEmpty()) {
      return ConnectorCredentialsProvider.fromJson(credentialsJson);
    }

    return ConnectorCredentialsProvider.fromDefault();
  }

  public static ConnectorCredentialsProvider getServiceAccountFromFile(String credentialsSAPath) {
    return new ConnectorCredentialsProvider(
        () ->
            ServiceAccountCredentials.fromStream(new FileInputStream(credentialsSAPath))
                .createWithUseJwtAccessWithScope(true)
                .createScoped(GCP_SCOPE));
  }

  public static ConnectorCredentialsProvider getServiceAccountFromJson(String credentialsSAJson) {
    return new ConnectorCredentialsProvider(
        () ->
            ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(credentialsSAJson.getBytes()))
                .createWithUseJwtAccessWithScope(true)
                .createScoped(GCP_SCOPE));
  }

  /**
   * Prefer {@link #getServiceAccountFromFile(String)} instead due to a potential security risk. See
   * {@see <a
   * href="https://cloud.google.com/docs/authentication/external/externally-sourced-credentials">documentation</a>}
   * for more details. This method does not validate the credential configuration. The security risk
   * occurs when a credential configuration is accepted from a source that is not under your control
   * and used without validation on your side.
   */
  public static ConnectorCredentialsProvider fromFile(String credentialPath) {
    return new ConnectorCredentialsProvider(
        () ->
            GoogleCredentials.fromStream(new FileInputStream(credentialPath))
                .createScoped(GCP_SCOPE));
  }

  /**
   * Prefer {@link #getServiceAccountFromJson(String)} instead due to a potential security risk. See
   * {@see <a
   * href="https://cloud.google.com/docs/authentication/external/externally-sourced-credentials">documentation</a>}
   * for more details. This method does not validate the credential configuration. The security risk
   * occurs when a credential configuration is accepted from a source that is not under your control
   * and used without validation on your side.
   */
  public static ConnectorCredentialsProvider fromJson(String credentialsJson) {
    return new ConnectorCredentialsProvider(
        () ->
            GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes()))
                .createScoped(GCP_SCOPE));
  }

  public static ConnectorCredentialsProvider fromDefault() {
    return new ConnectorCredentialsProvider(
        () -> GoogleCredentials.getApplicationDefault().createScoped(GCP_SCOPE));
  }

  @Override
  public Credentials getCredentials() throws IOException {
    return impl.getCredentials();
  }

  /**
   * Whether {@link #close()} releases anything.
   *
   * @return true for a configured provider class that is {@link AutoCloseable}
   */
  public boolean holdsResources() {
    return resource != null;
  }

  /** Closes the configured provider class if it is {@link AutoCloseable}. */
  @Override
  public void close() {
    if (resource == null) {
      return;
    }
    try {
      resource.close();
    } catch (Exception e) {
      log.warn("Failed to close the GCP credentials provider", e);
    }
  }
}
