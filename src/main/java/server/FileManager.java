package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FileManager {

    private final Path configFile;
    private final Path logFile;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileManager(String configFilePath, String logFilePath) {
        this.configFile = Paths.get(configFilePath);
        this.logFile = Paths.get(logFilePath);
    }

    public List<String> readConfig() throws IOException {
        System.out.println("Reading config from: " + configFile.toAbsolutePath());
        if (Files.notExists(configFile)) {
            List<String> defaultConfig = List.of(
                "server_port=8080",
                "server_name=DefaultServer",
                "log_level=INFO"
            );
            Path parentDir = configFile.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.write(configFile, defaultConfig);
            System.out.println("Default configuration file created at: " + configFile.toAbsolutePath());
            return defaultConfig;
        }
        return Files.readAllLines(configFile);
    }

    public void log(String message) {
        try {
            Path parentDir = logFile.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            String timestamp = LocalDateTime.now().format(dtf);
            String logEntry = timestamp + " | " + message + System.lineSeparator();

            Files.write(
                logFile,
                logEntry.getBytes(),
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to write to log file: " + e.getMessage());
        }
    }
}