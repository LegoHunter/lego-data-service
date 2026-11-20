package com.vattima.lego.inventory.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Opens a JDBC connection from a MySQL option file (the {@code mysql --defaults-extra-file} format).
 *
 * <p>Only the {@code [client]} and {@code [mysql]} sections are read, and only {@code host},
 * {@code port}, {@code user}, {@code password} and {@code database}. The password is handed to the
 * driver and is never logged, printed or kept beyond the connection call. The tool never ships with
 * an option file: whoever runs {@code export} supplies their own read-only one.
 */
public final class MySqlOptionFile {

    private static final List<String> SECTIONS = List.of("client", "mysql");

    private final Map<String, String> values;

    private MySqlOptionFile(Map<String, String> values) {
        this.values = values;
    }

    public static MySqlOptionFile read(Path path) throws IOException {
        return parse(Files.readAllLines(path));
    }

    static MySqlOptionFile parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        String section = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                continue;
            }
            if (section == null || !SECTIONS.contains(section)) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = line.substring(0, equals).strip().toLowerCase(Locale.ROOT).replace('-', '_');
            values.put(key, unquote(line.substring(equals + 1).strip()));
        }
        return new MySqlOptionFile(values);
    }

    /**
     * The JDBC URL without credentials, safe to print.
     */
    public String jdbcUrl(String defaultDatabase) {
        String host = values.getOrDefault("host", "localhost");
        String port = values.getOrDefault("port", "3306");
        String database = values.getOrDefault("database", defaultDatabase);
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?connectionTimeZone=UTC";
    }

    public String user() {
        return values.get("user");
    }

    public Connection connect(String defaultDatabase) throws SQLException {
        Properties properties = new Properties();
        if (values.containsKey("user")) {
            properties.setProperty("user", values.get("user"));
        }
        if (values.containsKey("password")) {
            properties.setProperty("password", values.get("password"));
        }
        return DriverManager.getConnection(jdbcUrl(defaultDatabase), properties);
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
