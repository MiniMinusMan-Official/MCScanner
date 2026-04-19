package com.mcscanner.screen;

import com.mcscanner.api.ServerData;
import com.mcscanner.api.ServerScannerAPI;
import com.mcscanner.api.ServerScannerAPI.PlayerHistory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ServerDetailScreen extends Screen {

    private final Screen parent;
    private final ServerData server;
    private volatile List<PlayerHistory> players = new ArrayList<>();
    private volatile boolean loading = true;
    private volatile String error = null;
    private int scrollOffset = 0;

    private EditBox serverNameField;
    private String statusMsg = null;
    private int statusColor = 0;

    private static final int VIOLET = 0xFFC084FC;
    private static final int GREEN = 0xFF6EE7B7;
    private static final int RED = 0xFFF87171;
    private static final int AMBER = 0xFFFBBF24;
    private static final int DIM = 0xFF52525B;
    private static final int LABEL = 0xFF71717A;
    private static final int TEXT = 0xFFA1A1AA;
    private static final int BRIGHT = 0xFFE4E4E7;

    public ServerDetailScreen(Screen parent, ServerData server) {
        super(Component.literal(server.getFullAddress()));
        this.parent = parent;
        this.server = server;
    }

    @Override
    protected void init() {
        int btnY = this.height - 24;
        int x = 4;

        // Back
        addRenderableWidget(Button.builder(
                Component.literal("\u2190 Back"), btn -> onClose()
        ).bounds(x, btnY, 52, 20).build());
        x += 56;

        // Copy IP
        addRenderableWidget(Button.builder(
                Component.literal("Copy IP"), btn -> {
                    if (minecraft != null) {
                        minecraft.keyboardHandler.setClipboard(server.getIpString());
                    }
                }
        ).bounds(x, btnY, 52, 20).build());
        x += 56;

        // Copy IP:Port (only if non-default port)
        if (server.port != 25565) {
            addRenderableWidget(Button.builder(
                    Component.literal("Copy IP:Port"), btn -> {
                        if (minecraft != null) {
                            minecraft.keyboardHandler.setClipboard(server.getFullAddress());
                        }
                    }
            ).bounds(x, btnY, 72, 20).build());
            x += 76;
        }

        // Direct Join
        addRenderableWidget(Button.builder(
                Component.literal("\u00A7aJoin"), btn -> directJoin()
        ).bounds(x, btnY, 40, 20).build());
        x += 44;

        // Server name field + Add to List button
        serverNameField = new EditBox(this.font, x, btnY, 90, 20, Component.literal("Name"));
        serverNameField.setHint(Component.literal("\u00A78Server Name"));
        serverNameField.setMaxLength(64);
        serverNameField.setValue(server.getIpString());
        addRenderableWidget(serverNameField);
        x += 94;

        addRenderableWidget(Button.builder(
                Component.literal("\u00A7b+List"), btn -> addToServerList()
        ).bounds(x, btnY, 40, 20).build());

        // Fetch player history async
        ServerScannerAPI.getPlayerHistory(server.ip, server.port).thenAccept(result -> {
            this.players = result.players;
            this.loading = false;
        }).exceptionally(e -> {
            this.error = "Failed to load players.";
            this.loading = false;
            return null;
        });
    }

    private void directJoin() {
        if (minecraft == null) return;
        net.minecraft.client.multiplayer.ServerData mcServer =
                new net.minecraft.client.multiplayer.ServerData(
                        server.getIpString(),
                        server.getFullAddress(),
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER
                );
        ConnectScreen.startConnecting(this, minecraft,
                ServerAddress.parseString(server.getFullAddress()),
                mcServer, false, null);
    }

    private void addToServerList() {
        if (minecraft == null) return;
        String name = serverNameField.getValue().trim();
        if (name.isEmpty()) name = server.getIpString();

        net.minecraft.client.multiplayer.ServerData mcServer =
                new net.minecraft.client.multiplayer.ServerData(
                        name,
                        server.getFullAddress(),
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER
                );
        ServerList list = new ServerList(minecraft);
        list.load();
        list.add(mcServer, false);
        list.save();

        statusMsg = "Added \"" + name + "\" to server list!";
        statusColor = GREEN;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);

        int y = 8;
        int leftCol = 8;
        int rightCol = Math.max(this.width / 2 + 10, 200);

        // ── Header ──────────────────────────────────────────────
        String addr = server.getFullAddress();
        ctx.text(font, "\u00A7l\u00A7d" + addr, leftCol, y, BRIGHT, true);
        y += 14;

        // Status dot + last seen
        String seen = ServerData.timeAgo(server.lastSeen);
        int dotColor = server.getOnline() > 0 ? 0xFF10B981 : 0xFF52525B;
        ctx.fill(leftCol, y + 2, leftCol + 4, y + 6, dotColor);
        ctx.text(font, "  Last seen: " + seen, leftCol + 2, y, DIM);
        y += 14;

        // ── Separator ───────────────────────────────────────────
        ctx.fill(leftCol, y, this.width - 8, y + 1, 0xFF27272A);
        y += 6;

        // ── Server Info (left column) ───────────────────────────
        ctx.text(font, "\u00A7lSERVER INFO", leftCol, y, LABEL);
        y += 12;

        y = drawRow(ctx, leftCol, y, "Version", server.getVersionName(), TEXT);
        y = drawRow(ctx, leftCol, y, "Players", server.getOnline() + " / " + server.getMax(),
                server.getOnline() > 0 ? GREEN : DIM);
        y = drawRow(ctx, leftCol, y, "Country", server.getCountry(), TEXT);
        y = drawRow(ctx, leftCol, y, "Provider", server.org != null ? server.org : "N/A", TEXT);

        // Auth
        String authText = "N/A";
        int authColor = TEXT;
        if (server.cracked != null) {
            authText = server.cracked ? "Cracked" : "Premium";
            authColor = server.cracked ? RED : GREEN;
        }
        y = drawRow(ctx, leftCol, y, "Auth", authText, authColor);

        // Whitelist
        String wlText = "N/A";
        int wlColor = TEXT;
        if (server.whitelisted != null) {
            wlText = server.whitelisted ? "Enabled" : "Off";
            wlColor = server.whitelisted ? AMBER : TEXT;
        }
        y = drawRow(ctx, leftCol, y, "Whitelist", wlText, wlColor);

        // ── MOTD ────────────────────────────────────────────────
        y += 6;
        ctx.text(font, "\u00A7lMOTD", leftCol, y, LABEL);
        y += 11;

        String motd = server.getMotd();
        if (motd.isEmpty()) motd = "\u00A7oNone";
        // Wrap MOTD text
        int maxW = this.width - leftCol - 12;
        for (String line : wrapText(motd, maxW)) {
            ctx.text(font, line, leftCol + 4, y, DIM);
            y += 10;
        }

        // ── Player History (right column or below) ──────────────
        int playerY = 8 + 14 + 14 + 6;
        int playerX = rightCol;

        // If screen is narrow, draw below instead
        if (this.width < 350) {
            playerX = leftCol;
            playerY = y + 8;
        }

        ctx.text(font, "\u00A7lPLAYER HISTORY", playerX, playerY, LABEL);

        // Online count pill
        String pill = " " + server.getOnline() + "/" + server.getMax() + " ";
        int pillX = playerX + font.width("\u00A7lPLAYER HISTORY") + 6;
        ctx.fill(pillX - 1, playerY - 1, pillX + font.width(pill) + 1, playerY + 9, 0x30C084FC);
        ctx.text(font, pill, pillX, playerY, VIOLET);
        playerY += 14;

        // Player list
        int playerListBot = this.height - 30;
        ctx.enableScissor(playerX, playerY, this.width - 4, playerListBot);

        if (loading) {
            ctx.text(font, "Loading...", playerX + 4, playerY + 4, DIM);
        } else if (error != null) {
            ctx.text(font, error, playerX + 4, playerY + 4, RED);
        } else if (players.isEmpty()) {
            ctx.text(font, "No player history", playerX + 4, playerY + 4, DIM);
        } else {
            int py = playerY - scrollOffset;
            for (PlayerHistory p : players) {
                if (py + 12 > playerY && py < playerListBot) {
                    ctx.text(font, "  \u00A77" + p.name, playerX + 2, py, TEXT);
                }
                py += 12;
            }
        }

        ctx.disableScissor();

        // Status message (added to list confirmation)
        if (statusMsg != null) {
            ctx.text(font, statusMsg, 4, this.height - 38, statusColor);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  INPUT
    // ════════════════════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int maxScroll = Math.max(0, players.size() * 12 - (this.height - 80));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (vAmt * 12)));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private int drawRow(GuiGraphicsExtractor ctx, int x, int y, String label, String value, int valueColor) {
        ctx.text(font, label, x + 4, y, LABEL);
        ctx.text(font, value, x + 60, y, valueColor);
        ctx.fill(x + 4, y + 10, x + 160, y + 11, 0x10FFFFFF);
        return y + 13;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            if (font.width(rawLine) <= maxWidth) {
                lines.add(rawLine);
            } else {
                StringBuilder current = new StringBuilder();
                for (String word : rawLine.split(" ")) {
                    String test = current.isEmpty() ? word : current + " " + word;
                    if (font.width(test) > maxWidth) {
                        if (!current.isEmpty()) lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(test);
                    }
                }
                if (!current.isEmpty()) lines.add(current.toString());
            }
        }
        return lines;
    }
}
