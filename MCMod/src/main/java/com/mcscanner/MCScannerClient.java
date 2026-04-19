package com.mcscanner;

import com.mcscanner.screen.ScannerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCScannerClient implements ClientModInitializer {

    public static final String MOD_ID = "mcscanner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof JoinMultiplayerScreen) {
                Button scannerBtn = Button.builder(
                        Component.literal("\u00A7dMC Scanner"),
                        btn -> client.setScreen(new ScannerScreen())
                ).bounds(width / 2 + 158, height - 28, 80, 20).build();
                Screens.getWidgets(screen).add(scannerBtn);
            }
        });

        LOGGER.info("[MC Scanner] Loaded!");
    }
}
