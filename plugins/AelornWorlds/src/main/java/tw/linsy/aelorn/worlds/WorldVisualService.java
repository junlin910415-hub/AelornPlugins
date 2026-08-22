package tw.linsy.aelorn.worlds;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Applies per-world settings: difficulty, PVP, spawn point, view/simulation
 * distance, time and weather locks, game rules, spawn budget and world border.
 *
 * Every mutation here touches world-wide state, so all of it is funnelled onto
 * the global region scheduler — never called directly from a command or event
 * thread.
 */
final class WorldVisualService {

    private static final long TICKS_PER_SECOND = 20L;

    /** Registry key of the rule that replaced keep-spawn-in-memory, in normalised form. */
    private static final String SPAWN_CHUNK_RADIUS_RULE = "spawnchunkradius";

    private final AelornWorldsPlugin plugin;

    /**
     * Rule name to rule, read from the server's game rule registry. Config keys
     * are matched with underscores and case stripped, so both the vanilla
     * {@code doMobSpawning} and the registry's {@code do_mob_spawning} resolve.
     */
    private final Map<String, GameRule<?>> rulesByName;

    WorldVisualService(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
        this.rulesByName = indexGameRules(plugin);
    }

    /** Schedules application for every live configured world. */
    void applyAll(WorldRegistry registry, GlobalSettings globals) {
        if (registry.live().isEmpty()) {
            return;
        }
        plugin.schedulers().global(() -> {
            int applied = 0;
            for (WorldProfile profile : registry.live()) {
                World world = Bukkit.getWorld(profile.name());
                if (world != null && applyNow(world, profile, globals)) {
                    applied++;
                }
            }
            if (applied > 0) {
                plugin.getLogger().info("已套用 " + applied + " 個世界的設定。");
            }
        });
    }

    /** Schedules application for a single world (used on WorldLoadEvent). */
    void apply(World world, WorldProfile profile, GlobalSettings globals) {
        plugin.schedulers().global(() -> applyNow(world, profile, globals));
    }

    /** Must run on the global region scheduler. */
    private boolean applyNow(World world, WorldProfile profile, GlobalSettings globals) {
        GlobalSettings.Apply apply = globals.apply();
        try {
            if (apply.difficulty() && profile.difficulty() != null) {
                world.setDifficulty(profile.difficulty());
            }
            if (apply.pvp() && profile.pvp() != null) {
                applyPvp(world, profile.pvp());
            }
            if (apply.spawnPoint() && profile.spawn() != null) {
                world.setSpawnLocation(profile.spawn().toLocation(world));
            }
            if (apply.visual()) {
                applyVisual(world, profile.visual(), apply);
            }
            if (apply.border()) {
                applyBorder(world, profile.border());
            }
            applySpawnBudget(world, profile.spawnSettings());
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "套用世界設定失敗: " + profile.name(), failure);
            return false;
        }
    }

    // World#setPVP is deprecated with no API replacement in this Paper build; it
    // is still the only way for a plugin to set per-world PVP.
    @SuppressWarnings("deprecation")
    private static void applyPvp(World world, boolean pvp) {
        world.setPVP(pvp);
    }

    /**
     * Per-world mob budget. This is the strongest per-world performance lever the
     * Bukkit API offers: bukkit.yml only has server-wide values, so a quiet build
     * world and a busy dungeon otherwise share one spawn budget.
     */
    private void applySpawnBudget(World world, WorldProfile.SpawnSettings spawnSettings) {
        if (spawnSettings.isEmpty()) {
            return;
        }
        spawnSettings.limits().forEach(world::setSpawnLimit);
        spawnSettings.tickRates().forEach(world::setTicksPerSpawns);
    }

    private void applyVisual(World world, WorldProfile.VisualSettings visual, GlobalSettings.Apply apply) {
        if (!visual.hasAnyOverride()) {
            return;
        }
        if (visual.viewDistance() > 0) {
            world.setViewDistance(visual.viewDistance());
        }
        if (visual.simulationDistance() > 0) {
            world.setSimulationDistance(visual.simulationDistance());
        }
        if (visual.sendViewDistance() > 0) {
            world.setSendViewDistance(visual.sendViewDistance());
        }
        if (visual.autoSave() != null) {
            world.setAutoSave(visual.autoSave());
        }
        if (apply.spawnChunkRadius() && visual.spawnChunkRadius() >= 0) {
            applySpawnChunkRadius(world, visual.spawnChunkRadius());
        }
        if (visual.lockedTime() >= 0L) {
            // Freezing the clock only sticks if the daylight cycle stops advancing it.
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setTime(visual.lockedTime());
        }
        applyWeatherLock(world, visual.weather());
        applyGameRules(world, visual.gameRules());
    }

    /**
     * How many chunks around spawn stay ticking. This replaced the old
     * keep-spawn-in-memory flag; 0 lets the spawn area unload entirely, which is
     * what an idle build or dungeon world wants.
     */
    @SuppressWarnings("unchecked")
    private void applySpawnChunkRadius(World world, int radius) {
        GameRule<?> rule = rulesByName.get(SPAWN_CHUNK_RADIUS_RULE);
        if (rule == null || rule.getType() != Integer.class) {
            plugin.getLogger().warning(
                "此伺服器版本沒有 spawnChunkRadius game rule，visual.spawn-chunk-radius 已略過。");
            return;
        }
        world.setGameRule((GameRule<Integer>) rule, radius);
    }

    /**
     * Vanilla world border. Cheapest possible cap on terrain generation, and the
     * one lever that actually bounds a world's disk footprint over time.
     */
    private void applyBorder(World world, WorldProfile.BorderSettings settings) {
        if (!settings.hasAnyOverride()) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        if (settings.centerX() != null || settings.centerZ() != null) {
            double x = settings.centerX() != null ? settings.centerX() : border.getCenter().getX();
            double z = settings.centerZ() != null ? settings.centerZ() : border.getCenter().getZ();
            border.setCenter(x, z);
        }
        if (settings.size() > 0.0D) {
            double size = Math.min(settings.size(), border.getMaxSize());
            if (settings.transitionSeconds() > 0L) {
                border.changeSize(size, settings.transitionSeconds() * TICKS_PER_SECOND);
            } else {
                border.setSize(size);
            }
        }
        if (settings.warningDistance() >= 0) {
            border.setWarningDistance(settings.warningDistance());
        }
        if (settings.warningTimeSeconds() >= 0) {
            border.setWarningTimeTicks(settings.warningTimeSeconds() * (int) TICKS_PER_SECOND);
        }
        if (settings.damageAmount() >= 0.0D) {
            border.setDamageAmount(settings.damageAmount());
        }
        if (settings.damageBuffer() >= 0.0D) {
            border.setDamageBuffer(settings.damageBuffer());
        }
    }

    private void applyWeatherLock(World world, WorldProfile.WeatherLock weather) {
        if (weather == WorldProfile.WeatherLock.NONE) {
            return;
        }
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        switch (weather) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(Integer.MAX_VALUE);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setThunderDuration(Integer.MAX_VALUE);
            }
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyGameRules(World world, Map<String, String> gameRules) {
        for (Map.Entry<String, String> entry : gameRules.entrySet()) {
            GameRule<?> rule = rulesByName.get(normalize(entry.getKey()));
            if (rule == null) {
                plugin.getLogger().warning("未知的 game rule: " + entry.getKey()
                    + "（此伺服器版本不認得這個名稱，請對照 /gamerule 的可用清單）");
                continue;
            }
            String raw = entry.getValue();
            try {
                if (rule.getType() == Boolean.class) {
                    Boolean value = parseBoolean(raw);
                    if (value == null) {
                        // Boolean.parseBoolean maps every unrecognised word to false,
                        // so "yes" or a typo would quietly switch a rule off and look
                        // like the setting had been applied.
                        plugin.getLogger().warning("game rule " + entry.getKey()
                            + " 需要 true 或 false，實際為: " + raw + "（已略過,未變更此規則）");
                        continue;
                    }
                    world.setGameRule((GameRule<Boolean>) rule, value);
                } else if (rule.getType() == Integer.class) {
                    world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(raw.trim()));
                } else {
                    plugin.getLogger().warning("不支援的 game rule 型別: " + entry.getKey());
                }
            } catch (NumberFormatException invalid) {
                plugin.getLogger().warning("game rule " + entry.getKey() + " 需要整數值，實際為: " + raw);
            }
        }
    }

    /** {@code null} for anything that is not unambiguously true or false. */
    private static Boolean parseBoolean(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("true")) {
            return Boolean.TRUE;
        }
        return value.equals("false") ? Boolean.FALSE : null;
    }

    private static Map<String, GameRule<?>> indexGameRules(AelornWorldsPlugin plugin) {
        Map<String, GameRule<?>> index = new HashMap<>();
        try {
            Registry<GameRule<?>> registry =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE);
            for (NamespacedKey key : registry.keyStream().toList()) {
                GameRule<?> rule = registry.get(key);
                if (rule != null) {
                    index.put(normalize(key.getKey()), rule);
                }
            }
        } catch (RuntimeException unavailable) {
            plugin.getLogger().log(Level.WARNING,
                "無法讀取 game rule 註冊表，visual.game-rules 設定將被忽略。", unavailable);
            return Map.of();
        }
        return Map.copyOf(index);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "");
    }
}
