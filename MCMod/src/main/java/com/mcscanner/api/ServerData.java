package com.mcscanner.api;

import com.google.gson.JsonElement;

/**
 * Data model matching the cornbread2100 API response for a single server.
 */
public class ServerData {

    public long ip;
    public int port;
    public VersionInfo version;
    public PlayersInfo players;
    public GeoInfo geo;
    public String org;
    public Boolean cracked;
    public Boolean whitelisted;
    public JsonElement description;
    public long lastSeen;

    public static class VersionInfo {
        public String name;
    }

    public static class PlayersInfo {
        public int online;
        public int max;
    }

    public static class GeoInfo {
        public String country;
    }

    public String getIpString() {
        long a = (ip >> 24) & 0xFF;
        long b = (ip >> 16) & 0xFF;
        long c = (ip >> 8) & 0xFF;
        long d = ip & 0xFF;
        return a + "." + b + "." + c + "." + d;
    }

    public String getFullAddress() {
        String base = getIpString();
        return port == 25565 ? base : base + ":" + port;
    }

    public String getMotd() {
        if (description == null) return "";
        if (description.isJsonPrimitive()) return description.getAsString();
        if (description.isJsonObject()) {
            var obj = description.getAsJsonObject();
            if (obj.has("text")) return obj.get("text").getAsString();
            // Try "extra" array
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                var sb = new StringBuilder();
                if (obj.has("text")) sb.append(obj.get("text").getAsString());
                for (var e : obj.getAsJsonArray("extra")) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("text")) {
                        sb.append(e.getAsJsonObject().get("text").getAsString());
                    } else if (e.isJsonPrimitive()) {
                        sb.append(e.getAsString());
                    }
                }
                return sb.toString();
            }
        }
        return description.toString();
    }

    public String getVersionName() {
        return version != null && version.name != null ? version.name : "N/A";
    }

    public int getOnline() {
        return players != null ? players.online : 0;
    }

    public int getMax() {
        return players != null ? players.max : 0;
    }

    public String getCountry() {
        return geo != null && geo.country != null ? geo.country : "??";
    }

    public static String timeAgo(long unixSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long diff = now - unixSeconds;
        if (diff < 0) return "now";
        if (diff < 60) return diff + "s ago";
        if (diff < 3600) return (diff / 60) + "m ago";
        if (diff < 86400) return (diff / 3600) + "h ago";
        return (diff / 86400) + "d ago";
    }
}
