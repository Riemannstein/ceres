package com.blokaly.ceres.common;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Configs {
  private static final Logger log = LoggerFactory.getLogger(Configs.class);



  private final Config javavm = ConfigFactory.systemProperties();

  private Config composite() {
    return ConfigFactory.systemEnvironment()
        .withFallback(javavm)
        .withFallback(new Builder().withSecureConf().envAwareApp().build())
        .withFallback(ConfigFactory.parseResources("overrides.conf"))
        .withFallback(ConfigFactory.parseResources("defaults.conf"));
  }

  public static Config getConfig() {
    return new Configs().composite();
  }

  // This should return the current executing user path
  private String getExecutionDirectory() {
    return javavm.getString("user.dir");
  }

  public static final BiFunction<Config, String, String> STRING_EXTRACTOR = Config::getString;
  public static final BiFunction<Config, String, Boolean> BOOLEAN_EXTRACTOR = Config::getBoolean;

  public static Map<String, Object> asMap(Config config) {
    return config.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().unwrapped()));
  }

  public static <T> T getOrDefault(Config config, String path, BiFunction<Config, String, T> extractor, T defaultValue) {
    if (config.hasPath(path)) {
      return extractor.apply(config, path);
    }
    return defaultValue;
  }

  public static <T> T getOrDefault(Config config, String path, BiFunction<Config, String, T> extractor, Supplier<T> defaultSupplier) {
    if (config.hasPath(path)) {
      return extractor.apply(config, path);
    }
    return defaultSupplier.get();
  }

  private class Builder {
    private Config conf;

    private Builder() {
      log.info("Loading configs first row is highest priority, second row is fallback and so on");
    }

    private Builder withResource(String resource) {
      conf = returnOrFallback(ConfigFactory.parseResources(resource));
      log.info("Loaded config file from resource ({})", resource);
      return this;
    }

    private Builder withOptionalFile(String path) {
      File secureConfFile = new File(path);
      if (secureConfFile.exists()) {
        log.info("Loaded config file from path ({})", path);
        conf = returnOrFallback(ConfigFactory.parseFile(secureConfFile));
      } else {
        log.info("Attempted to load file from path ({}) but it was not found", path);
      }
      return this;
    }

    private Builder envAwareApp() {
      String env = javavm.hasPath("env") ? javavm.getString("env") : "local";
      String envFile = "application." + env + ".conf";
      return withResource(envFile).withResource("application.conf");
    }

    private Builder withSecureConf() {
      return withOptionalFile(getExecutionDirectory() + "/secure.conf");
    }

    private Config build() {
      // Resolve substitutions.
      conf = conf.resolve();
      if (log.isDebugEnabled()) {
        log.debug("Logging properties. Make sure sensitive data such as passwords or secrets are not logged!");
        log.debug(conf.root().render(ConfigRenderOptions.concise().setFormatted(true)));
      }
      return conf;
    }

    private Config returnOrFallback(Config config) {
      if (this.conf == null) {
        return config;
      }
      return this.conf.withFallback(config);
    }
  }

}