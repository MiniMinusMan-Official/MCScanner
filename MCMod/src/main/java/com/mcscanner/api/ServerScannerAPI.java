package com.mcscanner.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcscanner.MCScannerClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerScannerAPI {

    private static final String BASE_URL = "https://api.cornbread2100.com/v1";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    // ── Search params ────────────────────────────────────────────

    public static class SearchParams {
        public String version = "";
        public String country = "";
        public String playersFilter = "any";   // "any" or "true"
        public String whitelistFilter = "any";  // "any", "true", "false"
        public String authFilter = "any";       // "any", "true", "false"
        public String sampleFilter = "any";     // "any", "true", "false"
        public String fullFilter = "any";       // "any", "true", "false"
        public int limit = 10;
        public int page = 1;
    }

    // ── Search result ────────────────────────────────────────────

    public static class SearchResult {
        public final List<ServerData> servers;
        public final int credits;

        public SearchResult(List<ServerData> servers, int credits) {
            this.servers = servers;
            this.credits = credits;
        }
    }

    // ── Player history entry ─────────────────────────────────────

    public static class PlayerHistory {
        public String name;
    }

    public static class PlayerHistoryResult {
        public final List<PlayerHistory> players;
        public final int credits;

        public PlayerHistoryResult(List<PlayerHistory> players, int credits) {
            this.players = players;
            this.credits = credits;
        }
    }

    // ── Search servers ───────────────────────────────────────────

    public static CompletableFuture<SearchResult> searchServers(SearchParams p) {
        StringBuilder url = new StringBuilder(BASE_URL + "/servers?");
        url.append("limit=").append(p.limit);
        url.append("&skip=").append(p.limit * (p.page - 1));
        url.append("&sort=lastSeen&descending=true");

        if (!p.version.isBlank())
            url.append("&version=").append(encode(p.version));
        if (!p.country.isBlank())
            url.append("&country=").append(encode(p.country));
        if ("true".equals(p.playersFilter))
            url.append("&minPlayers=1");
        if (!"any".equals(p.whitelistFilter))
            url.append("&whitelisted=").append(p.whitelistFilter);
        if (!"any".equals(p.authFilter))
            url.append("&cracked=").append(p.authFilter);
        if (!"any".equals(p.sampleFilter))
            url.append("&hasPlayerSample=").append(p.sampleFilter);
        if (!"any".equals(p.fullFilter))
            url.append("&full=").append(p.fullFilter);

        return get(url.toString()).thenApply(body -> {
            JsonElement root = JsonParser.parseString(body);
            List<ServerData> servers = new ArrayList<>();
            int credits = 0;

            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else {
                JsonObject obj = root.getAsJsonObject();
                credits = obj.has("credits") ? obj.get("credits").getAsInt() : 0;
                arr = findArray(obj);
            }

            if (arr != null) {
                for (JsonElement elem : arr) {
                    try {
                        servers.add(GSON.fromJson(elem, ServerData.class));
                    } catch (Exception e) {
                        MCScannerClient.LOGGER.warn("Failed to parse server entry", e);
                    }
                }
            }
            return new SearchResult(servers, credits);
        });
    }

    // ── Player history ───────────────────────────────────────────

    public static CompletableFuture<PlayerHistoryResult> getPlayerHistory(long ip, int port) {
        String url = BASE_URL + "/playerHistory?ip=" + ip + "&port=" + port;
        return get(url).thenApply(body -> {
            JsonElement root = JsonParser.parseString(body);
            List<PlayerHistory> players = new ArrayList<>();
            int credits = 0;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                credits = obj.has("credits") ? obj.get("credits").getAsInt() : 0;
                JsonArray arr = obj.has("data") ? obj.getAsJsonArray("data") : null;
                if (arr != null) {
                    for (JsonElement e : arr) {
                        try {
                            players.add(GSON.fromJson(e, PlayerHistory.class));
                        } catch (Exception ex) {
                            // skip invalid entries
                        }
                    }
                }
            }
            return new PlayerHistoryResult(players, credits);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static CompletableFuture<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "MCScanner-Mod/1.0")
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    private static JsonArray findArray(JsonObject obj) {
        for (String key : new String[]{"servers", "data", "results"}) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
