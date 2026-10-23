package cafe.controller;

import cafe.config.AppConfig;
import cafe.config.Database;
import cafe.model.NhanVien;
import cafe.service.Services;
import cafe.view.ChangePasswordDialog;
import cafe.view.FirstRunDialog;
import cafe.view.LoginFrame;
import cafe.view.MainFrame;
import cafe.view.common.Dialogs;
import cafe.view.common.UiTask;

import java.util.Arrays;

/**
 * Application flow: connect to the database -> (first-run manager setup) -> login ->
 * (forced password change) -> main window -> logout -> login again.
 */
public class AppController {

    private final AppConfig config;
    private Services services;
    private LoginFrame loginFrame;
    private MainFrame mainFrame;
    private boolean connecting;

    public AppController(AppConfig config) {
        this.config = config;
    }

    public void start() {
        loginFrame = new LoginFrame(this, config.get("shop.name"));
        loginFrame.setVisible(true);
        if (services == null) {
            connect();
        } else {
            loginFrame.setReady();
        }
    }

    /** Opens the database (creating tables on first run) without blocking the UI. */
    public void connect() {
        if (connecting || loginFrame == null) {
            return;
        }
        connecting = true;
        loginFrame.setBusy("Đang kết nối cơ sở dữ liệu...");
        UiTask.load(loginFrame, () -> {
            Database db = Database.fromConfig(config);
            if (config.getBool("db.autoInit")) {
                db.initializeIfEmpty(config.getBool("db.seedDemoData"));
                db.migrate();
            }
            Services s = new Services(config, db);
            boolean setup = s.auth.needsInitialSetup();
            boolean recoverOwner = !setup && s.auth.needsOwnerBootstrap();
            return new StartupResult(s, setup, recoverOwner);
        }, result -> {
            connecting = false;
            services = result.services();
            if (result.initialSetup()) {
                // Keep the login form out of sight while the modal first-run flow is active.
                loginFrame.setVisible(false);
                String username = FirstRunDialog.show(loginFrame, services);
                if (username == null) {
                    // A database without its initial owner cannot proceed to login.
                    loginFrame.dispose();
                    loginFrame = null;
                    return;
                }
                loginFrame.setUsername(username);
            } else if (result.ownerBootstrap()) {
                loginFrame.setVisible(false);
                String username = cafe.view.OwnerBootstrapDialog.show(loginFrame, services);
                if (username != null) {
                    loginFrame.setUsername(username);
                }
            }
            loginFrame.setVisible(true);
            loginFrame.setReady();
        }, error -> {
            connecting = false;
            loginFrame.setVisible(true);
            loginFrame.setConnectionError(Dialogs.userMessage(error)
                    + " Kiểm tra cấu hình kết nối trong tệp config.properties (xem README).");
        });
    }

    public void login(String username, char[] password) {
        loginFrame.setBusy("Đang đăng nhập...");
        UiTask.load(loginFrame, () -> {
            try {
                return services.auth.login(username, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, this::afterLogin, error -> {
            loginFrame.setReady();
            loginFrame.showError(Dialogs.userMessage(error));
        });
    }

    private void afterLogin(NhanVien user) {
        if (user.phaiDoiMatKhau() && !ChangePasswordDialog.show(loginFrame, services, true)) {
            UiTask.exec(loginFrame, services.auth::logout, loginFrame::setReady);
            return;
        }
        loginFrame.dispose();
        mainFrame = new MainFrame(this, services);
        mainFrame.setVisible(true);
    }

    public void logout() {
        if (!Dialogs.confirm(mainFrame, "Đăng xuất khỏi hệ thống?")) {
            return;
        }
        UiTask.exec(mainFrame, services.auth::logout, () -> {
            mainFrame.dispose();
            mainFrame = null;
            start();
        });
    }

    private record StartupResult(Services services, boolean initialSetup, boolean ownerBootstrap) {
    }
}
