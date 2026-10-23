package cafe.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application settings read from config.properties (UTF-8).
 * Location: -Dcafe.config=path, otherwise ./config.properties. Missing file = built-in defaults (embedded H2).
 */
public final class AppConfig {

    private final Properties props = new Properties();

    public AppConfig(Properties overrides) {
        props.setProperty("db.url", "jdbc:h2:./data/cafe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        props.setProperty("db.user", "sa");
        props.setProperty("db.password", "");
        props.setProperty("db.autoInit", "true");
        props.setProperty("db.seedDemoData", "true");
        props.setProperty("shop.name", "Cà Phê Hạt Nâu");
        props.setProperty("shop.address", "");
        props.setProperty("shop.phone", "");
        props.setProperty("inventory.allowNegativeStockOnSale", "false");
        props.setProperty("reservation.durationMinutes", "120");
        props.putAll(overrides);
    }

    public static AppConfig load() {
        Path path = Path.of(System.getProperty("cafe.config", "config.properties"));
        Properties fileProps = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                fileProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Không đọc được tệp cấu hình " + path, e);
            }
        }
        return new AppConfig(fileProps);
    }

    public String get(String key) {
        return props.getProperty(key, "").trim();
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }
}
