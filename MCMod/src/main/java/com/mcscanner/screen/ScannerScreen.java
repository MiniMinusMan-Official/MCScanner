package com.mcscanner.screen;

import com.mcscanner.api.ServerData;
import com.mcscanner.api.ServerScannerAPI;
import com.mcscanner.api.ServerScannerAPI.SearchParams;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ScannerScreen extends Screen {

    // ── Filter state ────────────────────────────────────────────
    private int playersIdx = 0;
    private int whitelistIdx = 0;
    private int authIdx = 0;
    private int sampleIdx = 0;
    private int fullIdx = 0;

    private static final String[] PLAYERS_LABELS = {"Any", "Active"};
    private static final String[] PLAYERS_VALUES = {"any", "true"};
    private static final String[] TRISTATE_LABELS = {"Any", "Yes", "No"};
    private static final String[] TRISTATE_VALUES = {"any", "true", "false"};
    private static final String[] AUTH_LABELS = {"Any", "Cracked", "Premium"};
    private static final String[] AUTH_VALUES = {"any", "true", "false"};

    // ── Widgets ─────────────────────────────────────────────────
    private EditBox versionField;
    private EditBox countryField;
    private EditBox limitField;
    private EditBox pageField;

    private Button playersBtn, whitelistBtn, authBtn, sampleBtn, fullBtn;
    private Button searchBtn, shuffleBtn, prevBtn, nextBtn;

    // ── Data ────────────────────────────────────────────────────
    private List<ServerData> servers = new ArrayList<>();
    private int currentPage = 1;
    private int credits = 0;
    private volatile boolean loading = false;
    private String errorMsg = null;
    private int scrollOffset = 0;

    // ── Saved field text (survives init() calls on resize) ──────
    private String savedVersion = "";
    private String savedCountry = "";
    private String savedLimit = "10";
    private String savedPage = "1";

    // ── Layout constants ────────────────────────────────────────
    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_COLOR = 0xFF71717A;
    private static final int VIOLET = 0xFFC084FC;
    private static final int GREEN = 0xFF6EE7B7;
    private static final int RED = 0xFFF87171;
    private static final int DIM = 0xFF52525B;
    private static final int TEXT_COLOR = 0xFFA1A1AA;
    private static final int BRIGHT = 0xFFE4E4E7;

    public ScannerScreen() {
        super(Component.literal("Server Scanner"));
    }

    // ════════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        // Save existing text field contents before recreation
        if (versionField != null) savedVersion = versionField.getValue();
        if (countryField != null) savedCountry = countryField.getValue();
        if (limitField != null) savedLimit = limitField.getValue();
        if (pageField != null) savedPage = pageField.getValue();

        int cx = this.width / 2;
        int btnH = 20;
        int gap = 2;

        // ── Row 1: 5 cycling filter buttons ─────────────────────
        int btnW = Math.min(78, (this.width - 30) / 5);
        int row1Y = 18;
        int totalRow1 = 5 * btnW + 4 * gap;
        int rx = cx - totalRow1 / 2;

        playersBtn = addCycleButton(rx, row1Y, btnW, btnH, "Players",
                PLAYERS_LABELS, playersIdx, i -> playersIdx = i);
        rx += btnW + gap;
        whitelistBtn = addCycleButton(rx, row1Y, btnW, btnH, "WList",
                TRISTATE_LABELS, whitelistIdx, i -> whitelistIdx = i);
        rx += btnW + gap;
        authBtn = addCycleButton(rx, row1Y, btnW, btnH, "Auth",
                AUTH_LABELS, authIdx, i -> authIdx = i);
        rx += btnW + gap;
        sampleBtn = addCycleButton(rx, row1Y, btnW, btnH, "Sample",
                TRISTATE_LABELS, sampleIdx, i -> sampleIdx = i);
        rx += btnW + gap;
        fullBtn = addCycleButton(rx, row1Y, btnW, btnH, "Full",
                TRISTATE_LABELS, fullIdx, i -> fullIdx = i);

        // ── Row 2: text fields + action buttons ─────────────────
        int row2Y = row1Y + btnH + gap + 2;
        int fieldW = Math.min(56, (this.width - 40) / 6);
        int actW = Math.min(62, fieldW + 4);
        int totalRow2 = 4 * fieldW + 2 * actW + 5 * gap;
        rx = cx - totalRow2 / 2;

        versionField = addTextField(rx, row2Y, fieldW, btnH, "Version");
        versionField.setValue(savedVersion);
        rx += fieldW + gap;

        countryField = addTextField(rx, row2Y, fieldW, btnH, "Country");
        countryField.setValue(savedCountry);
        rx += fieldW + gap;

        limitField = addTextField(rx, row2Y, fieldW, btnH, "Limit");
        limitField.setValue(savedLimit);
        rx += fieldW + gap;

        pageField = addTextField(rx, row2Y, fieldW, btnH, "Page");
        pageField.setValue(savedPage);
        rx += fieldW + gap;

        searchBtn = addRenderableWidget(Button.builder(
                Component.literal("\u00A7bSearch"), btn -> doSearch()
        ).bounds(rx, row2Y, actW, btnH).build());
        rx += actW + gap;

        shuffleBtn = addRenderableWidget(Button.builder(
                Component.literal("\u00A77Shuffle"), btn -> doShuffle()
        ).bounds(rx, row2Y, actW, btnH).build());

        // ── Bottom: pagination ──────────────────────────────────
        int botY = this.height - 24;
        prevBtn = addRenderableWidget(Button.builder(
                Component.literal("\u2190 Prev"), btn -> prevPage()
        ).bounds(4, botY, 50, btnH).build());

        nextBtn = addRenderableWidget(Button.builder(
                Component.literal("Next \u2192"), btn -> nextPage()
        ).bounds(58, botY, 50, btnH).build());

        scrollOffset = 0;
    }

    // ════════════════════════════════════════════════════════════
    //  RENDER
    // ════════════════════════════════════════════════════════════

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);

        // Title
        ctx.text(this.font, "\u00A7l\u00A7dServer Scanner", 6, 5, BRIGHT, true);

        // Column headers
        int headerY = 62;
        ctx.text(font, "IP", 6, headerY, HEADER_COLOR);
        ctx.text(font, "Ver", colX(0.28), headerY, HEADER_COLOR);
        ctx.text(font, "Plrs", colX(0.44), headerY, HEADER_COLOR);
        ctx.text(font, "CC", colX(0.56), headerY, HEADER_COLOR);
        ctx.text(font, "Crk", colX(0.64), headerY, HEADER_COLOR);
        ctx.text(font, "WHL", colX(0.74), headerY, HEADER_COLOR);
        ctx.text(font, "Seen", colX(0.85), headerY, HEADER_COLOR);

        // Separator line
        ctx.fill(4, headerY + 10, this.width - 4, headerY + 11, 0xFF27272A);

        // Server list area
        int listTop = headerY + 13;
        int listBot = this.height - 28;
        renderServerList(ctx, mx, my, listTop, listBot);

        // Status bar
        int botY = this.height - 12;
        String timer = getResetTimer();
        String status = "\u00A7aCredits: " + credits + "  \u00A7eReset: " + timer + "  \u00A77Page " + currentPage;
        ctx.text(font, status, this.width - font.width(status) - 6, botY, TEXT_COLOR);

        if (loading) {
            String msg = "\u00A7eSearching...";
            ctx.text(font, msg, cxText(msg), listTop + 30, BRIGHT);
        } else if (errorMsg != null) {
            ctx.text(font, "\u00A7c" + errorMsg, cxText(errorMsg), listTop + 30, RED);
        } else if (servers.isEmpty()) {
            String hint = "Press Search or Shuffle to find servers.";
            ctx.text(font, hint, cxText(hint), listTop + 30, DIM);
        }
    }

    private void renderServerList(GuiGraphicsExtractor ctx, int mx, int my, int top, int bot) {
        if (servers.isEmpty() || loading) return;

        ctx.enableScissor(0, top, this.width, bot);

        int y = top - scrollOffset;
        for (int i = 0; i < servers.size(); i++) {
            int rowTop = y;
            int rowBot = y + ROW_HEIGHT;

            if (rowBot > top && rowTop < bot) {
                ServerData s = servers.get(i);
                boolean hovered = mx >= 4 && mx < this.width - 4 && my >= rowTop && my < rowBot;

                // Hover background
                if (hovered) {
                    ctx.fill(4, rowTop, this.width - 4, rowBot, 0x20FFFFFF);
                    // Left accent bar
                    ctx.fill(4, rowTop, 6, rowBot, 0xFF7C3AED);
                }

                int textY = rowTop + (ROW_HEIGHT - 8) / 2;

                // IP
                ctx.text(font, s.getIpString(), 8, textY, VIOLET);

                // Version
                ctx.text(font, s.getVersionName(), colX(0.28), textY, TEXT_COLOR);

                // Players
                int online = s.getOnline();
                int max = s.getMax();
                int plrColor = online > 0 ? GREEN : DIM;
                ctx.text(font, online + "/" + max, colX(0.44), textY, plrColor);

                // Country
                ctx.text(font, s.getCountry(), colX(0.56), textY, TEXT_COLOR);

                // Cracked
                if (s.cracked != null && s.cracked) {
                    ctx.text(font, "YES", colX(0.64), textY, RED);
                } else if (s.cracked != null) {
                    ctx.text(font, "no", colX(0.64), textY, DIM);
                } else {
                    ctx.text(font, "-", colX(0.64), textY, DIM);
                }

                // Whitelist
                if (s.whitelisted != null && s.whitelisted) {
                    ctx.text(font, "YES", colX(0.74), textY, 0xFFFBBF24);
                } else if (s.whitelisted != null) {
                    ctx.text(font, "no", colX(0.74), textY, DIM);
                } else {
                    ctx.text(font, "-", colX(0.74), textY, DIM);
                }

                // Last seen
                ctx.text(font, ServerData.timeAgo(s.lastSeen), colX(0.85), textY, DIM);

                // Row separator
                ctx.fill(4, rowBot - 1, this.width - 4, rowBot, 0x10FFFFFF);
            }
            y += ROW_HEIGHT;
        }

        ctx.disableScissor();
    }

    // ════════════════════════════════════════════════════════════
    //  INPUT
    // ════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (super.mouseClicked(event, focused)) return true;

        if (event.button() == 0 && !servers.isEmpty()) {
            double mx = event.x();
            double my = event.y();
            int listTop = 75;
            int listBot = this.height - 28;
            if (my >= listTop && my < listBot) {
                int relY = (int) my - listTop + scrollOffset;
                int idx = relY / ROW_HEIGHT;
                if (idx >= 0 && idx < servers.size()) {
                    if (minecraft != null) {
                        minecraft.setScreen(new ServerDetailScreen(this, servers.get(idx)));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int listTop = 75;
        int listBot = this.height - 28;
        int listHeight = listBot - listTop;
        int maxScroll = Math.max(0, servers.size() * ROW_HEIGHT - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (vAmt * ROW_HEIGHT)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Allow Enter to trigger search from text fields
        if (event.key() == 257 /* ENTER */ && (
                versionField.isFocused() || countryField.isFocused() ||
                limitField.isFocused() || pageField.isFocused())) {
            doSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    // ════════════════════════════════════════════════════════════
    //  ACTIONS
    // ════════════════════════════════════════════════════════════

    private void doSearch() {
        if (loading) return;
        loading = true;
        errorMsg = null;
        scrollOffset = 0;

        // Parse page/limit from text fields
        currentPage = parseIntOr(pageField.getValue(), 1);
        if (currentPage < 1) currentPage = 1;

        SearchParams p = new SearchParams();
        p.version = versionField.getValue().trim();
        p.country = countryField.getValue().trim();
        p.limit = parseIntOr(limitField.getValue(), 10);
        p.page = currentPage;
        p.playersFilter = PLAYERS_VALUES[playersIdx];
        p.whitelistFilter = TRISTATE_VALUES[whitelistIdx];
        p.authFilter = AUTH_VALUES[authIdx];
        p.sampleFilter = TRISTATE_VALUES[sampleIdx];
        p.fullFilter = TRISTATE_VALUES[fullIdx];

        ServerScannerAPI.searchServers(p).thenAccept(result -> {
            servers = result.servers;
            credits = result.credits;
            loading = false;
        }).exceptionally(e -> {
            errorMsg = "Connection failed. Try again.";
            loading = false;
            return null;
        });
    }

    private void doShuffle() {
        Random r = new Random();
        String[] versions = {"1.8.9", "1.12.2", "1.16.5", "1.20.1", "26", "26.1", "26.1.1", "26.1.2"};
        versionField.setValue(versions[r.nextInt(versions.length)]);

        playersIdx = r.nextInt(PLAYERS_LABELS.length);
        playersBtn.setMessage(Component.literal("Players: " + PLAYERS_LABELS[playersIdx]));

        whitelistIdx = r.nextInt(TRISTATE_LABELS.length);
        whitelistBtn.setMessage(Component.literal("WList: " + TRISTATE_LABELS[whitelistIdx]));

        authIdx = r.nextInt(AUTH_LABELS.length);
        authBtn.setMessage(Component.literal("Auth: " + AUTH_LABELS[authIdx]));

        sampleIdx = r.nextInt(TRISTATE_LABELS.length);
        sampleBtn.setMessage(Component.literal("Sample: " + TRISTATE_LABELS[sampleIdx]));

        fullIdx = r.nextInt(TRISTATE_LABELS.length);
        fullBtn.setMessage(Component.literal("Full: " + TRISTATE_LABELS[fullIdx]));

        doSearch();
    }

    private void nextPage() {
        currentPage++;
        pageField.setValue(String.valueOf(currentPage));
        doSearch();
    }

    private void prevPage() {
        if (currentPage > 1) {
            currentPage--;
            pageField.setValue(String.valueOf(currentPage));
            doSearch();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private Button addCycleButton(int x, int y, int w, int h,
                                  String label, String[] options, int startIdx,
                                  java.util.function.IntConsumer onChanged) {
        final int[] idx = {startIdx};
        return addRenderableWidget(Button.builder(
                Component.literal(label + ": " + options[idx[0]]),
                btn -> {
                    idx[0] = (idx[0] + 1) % options.length;
                    onChanged.accept(idx[0]);
                    btn.setMessage(Component.literal(label + ": " + options[idx[0]]));
                }
        ).bounds(x, y, w, h).build());
    }

    private EditBox addTextField(int x, int y, int w, int h, String placeholder) {
        EditBox f = new EditBox(this.font, x, y, w, h, Component.literal(placeholder));
        f.setHint(Component.literal("\u00A78" + placeholder));
        f.setMaxLength(64);
        addRenderableWidget(f);
        return f;
    }

    private int colX(double frac) {
        return (int) (this.width * frac);
    }

    private int cxText(String text) {
        return (this.width - font.width(text)) / 2;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private String getResetTimer() {
        Instant now = Instant.now();
        long epochSec = now.getEpochSecond();
        long secIntoHour = epochSec % 3600;
        long secsLeft = 3600 - secIntoHour;
        long min = secsLeft / 60;
        long sec = secsLeft % 60;
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
