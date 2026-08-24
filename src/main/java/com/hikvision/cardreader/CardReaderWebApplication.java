package com.hikvision.cardreader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CardReaderWebApplication {
    private static final int PORT = Integer.getInteger("cardreader.port", 18080);
    private static final File WEB_ROOT = new File(System.getProperty("cardreader.web.root", "web"));
    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
            System.getProperty("cardreader.allowed.origins", "*").split(",")
    ));

    private CardReaderWebApplication() {
    }

    public static void main(String[] args) throws Exception {
        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        } catch (BindException error) {
            System.out.println("本地读卡服务已在运行，无需重复启动：http://127.0.0.1:" + PORT);
            showMessage("读卡服务已在运行", "本地读卡服务已经启动，无需重复运行。");
            return;
        }
        final CardReaderService cardReader = new CardReaderService();
        server.createContext("/api/status", exchange -> {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"仅支持 GET 请求\"}");
                return;
            }
            sendJson(exchange, 200, cardReader.connectionStatusJson());
        });
        server.createContext("/api/read-card", exchange -> {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"仅支持 POST 请求\"}");
                return;
            }
            try {
                sendJson(exchange, 200, cardReader.readOnce().toJson());
            } catch (CardReaderService.CardReaderException error) {
                sendJson(exchange, 400, error.toJson());
            } catch (Throwable error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"服务异常："
                        + escapeJson(message) + "\"}");
            }
        });
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.start();
        String url = "http://127.0.0.1:" + PORT;
        System.out.println("读卡网页已启动：" + url);
        System.out.println("关闭本窗口即可停止服务。");

        AtomicBoolean stopping = new AtomicBoolean();
        AtomicReference<TrayIcon> trayIconRef = new AtomicReference<>();
        Runnable shutdown = () -> {
            if (!stopping.compareAndSet(false, true)) {
                return;
            }
            TrayIcon trayIcon = trayIconRef.get();
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            cardReader.close();
            server.stop(0);
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown, "card-reader-shutdown"));
        trayIconRef.set(installTrayIcon(url, () -> {
            shutdown.run();
            System.exit(0);
        }));

        if (!"false".equalsIgnoreCase(System.getProperty("cardreader.openBrowser", "true"))
                && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
                System.out.println("请手动在浏览器打开：" + url);
            }
        }
    }

    private static TrayIcon installTrayIcon(String url, Runnable exitAction) {
        if (!SystemTray.isSupported()) {
            showMessage("读卡服务已启动", "本地读卡服务已启动，前端可以读取 IC 卡。");
            return null;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem statusItem = new MenuItem("海康IC读卡器运行中");
            statusItem.setEnabled(false);
            MenuItem openItem = new MenuItem("打开服务状态页");
            openItem.addActionListener(event -> openBrowser(url));
            MenuItem exitItem = new MenuItem("退出读卡服务");
            exitItem.addActionListener(event -> new Thread(exitAction, "card-reader-exit").start());
            menu.add(statusItem);
            menu.addSeparator();
            menu.add(openItem);
            menu.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(createTrayImage(), "海康IC读卡器", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> openBrowser(url));
            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.displayMessage("海康IC读卡器已启动", "本地服务正在运行，可以在绑定 IC 页面读取卡片。",
                    TrayIcon.MessageType.INFO);
            return trayIcon;
        } catch (Exception error) {
            showMessage("读卡服务已启动", "本地读卡服务已启动，前端可以读取 IC 卡。");
            return null;
        }
    }

    private static BufferedImage createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(45, 140, 240));
        graphics.fillRoundRect(3, 3, 26, 26, 8, 8);
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(8, 9, 16, 12, 3, 3);
        graphics.setColor(new Color(82, 196, 26));
        graphics.fillOval(21, 21, 8, 8);
        graphics.dispose();
        return image;
    }

    private static void openBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {
            // 状态页不是业务必需功能，打开失败时保持服务运行。
        }
    }

    private static void showMessage(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(
                null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isEmpty()) {
            String normalizedOrigin = origin.trim();
            boolean allowed = ALLOWED_ORIGINS.contains("*") || ALLOWED_ORIGINS.contains(normalizedOrigin);
            if (!allowed) {
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"当前网页来源未获授权\"}");
                return true;
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", normalizedOrigin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Accept, Content-Type");
            exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        }
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static final class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String fileName;
            if ("/".equals(path) || "/index.html".equals(path)) {
                fileName = "index.html";
            } else if ("/styles.css".equals(path)) {
                fileName = "styles.css";
            } else if ("/app.js".equals(path)) {
                fileName = "app.js";
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            File file = new File(WEB_ROOT, fileName);
            if (!file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = readAll(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(fileName));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private static byte[] readAll(File file) throws IOException {
            try (InputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }

        private static String contentType(String fileName) {
            if (fileName.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (fileName.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            return "text/html; charset=utf-8";
        }
    }
}
