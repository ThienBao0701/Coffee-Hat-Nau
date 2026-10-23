package cafe;

import cafe.config.AppConfig;
import cafe.controller.AppController;
import cafe.view.common.Theme;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Entry point: java -jar cafe-management.jar (or run this class from the IDE). */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        configureLogging();
        AppConfig config = AppConfig.load();
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new AppController(config).start();
        });
    }

    /** Logs go to logs/cafe-N.log (rotating). Passwords and DB credentials are never logged. */
    private static void configureLogging() {
        try {
            Files.createDirectories(Path.of("logs"));
            FileHandler file = new FileHandler("logs/cafe-%g.log", 2_000_000, 3, true);
            file.setFormatter(new SimpleFormatter());
            file.setEncoding("UTF-8");
            Logger.getLogger("").addHandler(file);
            Logger.getLogger("").setLevel(Level.INFO);
        } catch (IOException e) {
            Logger.getLogger(App.class.getName()).log(Level.WARNING, "File logging disabled", e);
        }
    }
}
