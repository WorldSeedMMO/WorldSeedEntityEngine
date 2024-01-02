package events;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.resourcepack.ResourcePack;
import net.minestom.server.timer.TaskSchedule;

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PackEvent {
    static class PackHandler implements HttpHandler {
        private final File zipFile;
        private final String expectedHash;

        public PackHandler(File zipFile, String expectedHash) {
            this.zipFile = zipFile;
            this.expectedHash = expectedHash;
        }

        public void handle(HttpExchange t) throws IOException {
            String requestHash = getHashFromQuery(t.getRequestURI().getQuery());
            if (expectedHash.equals(requestHash)) {
                t.sendResponseHeaders(200, zipFile.length());
                OutputStream os = t.getResponseBody();
                Files.copy(zipFile.toPath(), os);
                os.close();
            } else {
                t.sendResponseHeaders(404, 0); // Invalid hash, return 404
            }
        }

        private String getHashFromQuery(String query) {
            if (query != null && !query.isEmpty()) {
                Map<String, String> queryMap = new HashMap<>();
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    queryMap.put(pair[0], pair[1]);
                }
                return queryMap.get("hash");
            }
            return null;
        }
    }

    private static void startHttpServer(File zipFile, String hash) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/pack", new PackHandler(zipFile, hash));
        server.start();
    }

    private static String calculateMD5(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] md5sum = digest.digest();
        BigInteger bigInt = new BigInteger(1, md5sum);
        return String.format("%032x", bigInt);
    }

    public static void hook(GlobalEventHandler handler, File zipFile) throws IOException, NoSuchAlgorithmException {
        String hash = calculateMD5(zipFile);
        startHttpServer(zipFile, hash);

        handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                ResourcePack pack = ResourcePack.optional(UUID.randomUUID(), "http://127.0.0.1:8080/pack?hash=" + hash, hash, Component.text("WSEE Resource Pack"));
                event.getPlayer().setResourcePack(pack);
            }, TaskSchedule.tick(20), TaskSchedule.stop());
        });
    }
}
