package dev.elim.profiler.common.config;

import dev.elim.profiler.common.util.ProfilerLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigIO {
    private ConfigIO() {
    }

    public static ProfilerConfig load(Path dataDirectory, ClassLoader classLoader, ProfilerLogger logger) {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (Files.notExists(configPath)) {
                try (InputStream input = classLoader.getResourceAsStream("config.yml")) {
                    if (input == null) {
                        throw new IOException("Default config.yml is missing from the jar.");
                    }
                    try (OutputStream output = Files.newOutputStream(configPath)) {
                        input.transferTo(output);
                    }
                }
            }

            Yaml yaml = new Yaml();
            try (InputStream input = Files.newInputStream(configPath)) {
                Object loaded = yaml.load(input);
                if (loaded instanceof Map<?, ?> map) {
                    Map<String, Object> root = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        root.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    return ProfilerConfig.from(root);
                }
            }
        } catch (Exception exception) {
            logger.error("Failed to load config.yml, using defaults.", exception);
        }
        return ProfilerConfig.defaults();
    }
}
