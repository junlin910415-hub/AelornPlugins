package com.xuzhihuanjing.rpgcore.config;

import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HudSettingsTest {
    private HudSettingsTest() {
    }

    public static void main(String[] arguments) {
        YamlConfiguration defaults = new YamlConfiguration();
        HudSettings settings = HudSettings.from(defaults);
        require(settings.renderer() == HudSettings.Renderer.AELORIAHUD,
                "the 26.2 default renderer is not AeloriaHUD");
        require(settings.aeloriaAssetsEnabled() && settings.oraxenEnabled(),
                "the new asset accessor broke the 0.24.x ABI value");
        require(settings.requiredExternalPlugins().equals(Set.of("AeloriaHUD", "Nexo")),
                "AeloriaHUD/Nexo dependency gate drifted");

        YamlConfiguration unsupported = new YamlConfiguration();
        unsupported.set("hud.renderer", "internal");
        try {
            HudSettings.from(unsupported);
            throw new AssertionError("format-88 INTERNAL renderer was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("format-88/26.2"),
                    "unexpected INTERNAL fail-closed message: " + expected.getMessage());
        }

        System.out.println("HudSettingsTest PASS (AeloriaHUD default, INTERNAL fail-closed)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
