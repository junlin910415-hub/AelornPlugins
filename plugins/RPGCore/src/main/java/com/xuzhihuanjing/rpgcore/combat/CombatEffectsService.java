package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.CombatCoreSettings;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class CombatEffectsService {
    private final CombatCoreSettings.EffectSettings settings;

    public CombatEffectsService(CombatCoreSettings.EffectSettings settings) {
        this.settings = settings;
    }

    public void beginWindup(
            Player player,
            WeaponArchetype archetype,
            String cadenceProfileId,
            int windupTicks) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return;
        }
        Location origin = player.getEyeLocation()
                .add(player.getEyeLocation().getDirection().multiply(0.65));
        float volume = (float) settings.soundVolume();
        if (cadenceProfileId.equals("great-weapon") || cadenceProfileId.equals("arcane-staff")) {
            player.getWorld().spawnParticle(Particle.DUST, origin, 4, 0.12, 0.12, 0.12, 0.0,
                    dust(cadenceProfileId.equals("great-weapon")
                            ? Color.fromRGB(236, 205, 145)
                            : Color.fromRGB(151, 100, 255), 0.85f));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON,
                    volume * 0.5f, cadenceProfileId.equals("great-weapon") ? 0.58f : 1.22f);
        } else if (windupTicks >= 8) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP,
                    volume * 0.38f, archetype == WeaponArchetype.RANGER_BOW ? 1.45f : 0.92f);
        }
    }

    public void executeAttack(Player player, WeaponArchetype archetype, String cadenceProfileId) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return;
        }
        player.swingMainHand();
        Location origin = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.8));
        float volume = (float) settings.soundVolume();
        switch (archetype) {
            case VANGUARD_BLADE -> {
                int sweeps = cadenceProfileId.equals("great-weapon") ? 2 : 1;
                player.getWorld().spawnParticle(
                        Particle.SWEEP_ATTACK, origin, sweeps, 0.15, 0.12, 0.15, 0.0);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        volume, cadenceProfileId.equals("great-weapon") ? 0.62f : 0.86f);
            }
            case SHADOW_DAGGER -> {
                player.getWorld().spawnParticle(Particle.CRIT, origin, 5, 0.16, 0.12, 0.16, 0.03);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, volume * 0.72f, 1.55f);
            }
            case RANGER_BOW -> {
                player.getWorld().spawnParticle(Particle.SMALL_GUST, origin, 1, 0.08, 0.08, 0.08, 0.0);
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, volume * 0.8f, 1.7f);
            }
            case ARCANE_FOCUS -> {
                player.getWorld().spawnParticle(Particle.ENCHANT, origin, 9, 0.18, 0.18, 0.18, 0.12);
                player.getWorld().spawnParticle(Particle.DUST, origin, 3, 0.08, 0.08, 0.08, 0.0,
                        dust(Color.fromRGB(151, 100, 255), 1.0f));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume, 1.35f);
            }
            case WARDEN_TOTEM -> {
                player.getWorld().spawnParticle(Particle.WITCH, origin, 6, 0.16, 0.2, 0.16, 0.02);
                player.getWorld().spawnParticle(Particle.DUST, origin, 3, 0.08, 0.08, 0.08, 0.0,
                        dust(Color.fromRGB(74, 220, 184), 1.0f));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, 0.82f);
            }
        }
    }

    public void bufferedAttack(Player player, WeaponArchetype archetype) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                (float) settings.soundVolume() * 0.22f,
                archetype == WeaponArchetype.SHADOW_DAGGER ? 1.75f : 1.3f);
    }

    public void interruptedAttack(Player player, WeaponArchetype archetype) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) player)) {
            return;
        }
        Location origin = player.getLocation().add(0.0, 1.0, 0.0);
        player.getWorld().spawnParticle(Particle.SMOKE, origin, 4, 0.18, 0.2, 0.18, 0.015);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND,
                (float) settings.soundVolume() * 0.34f, 1.65f);
    }

    public void drawTrail(Player player, WeaponArchetype archetype, Location start, Location end) {
        if (!settings.enabled() || start.getWorld() == null || start.getWorld() != end.getWorld()) {
            return;
        }
        Vector delta = end.toVector().subtract(start.toVector());
        double distance = delta.length();
        if (distance <= 0.01) {
            return;
        }
        Vector step = delta.normalize().multiply(0.42);
        int points = Math.min(settings.maximumTrailPoints(), Math.max(1, (int) Math.ceil(distance / 0.42)));
        Location point = start.clone();
        Particle.DustOptions dust = dust(trailColor(archetype), archetype == WeaponArchetype.ARCANE_FOCUS ? 0.9f : 0.72f);
        for (int index = 0; index <= points; index++) {
            start.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (index % 5 == 0) {
                Particle accent = archetype == WeaponArchetype.WARDEN_TOTEM ? Particle.WITCH : Particle.ENCHANT;
                start.getWorld().spawnParticle(accent, point, 1, 0.02, 0.02, 0.02, 0.0);
            }
            point.add(step);
        }
    }

    public void showImpact(
            Player attacker,
            LivingEntity target,
            MonsterDefinition definition,
            double finalDamage,
            boolean critical) {
        if (!settings.enabled() || !Bukkit.isOwnedByCurrentRegion((Entity) target)) {
            return;
        }
        HitMaterial material = hitMaterial(definition);
        Location impact = target.getLocation().add(0.0, Math.max(0.45, target.getHeight() * 0.62), 0.0);
        int count = Math.min(settings.maximumImpactParticles(), Math.max(5, (int) Math.ceil(finalDamage * 0.55)));
        float volume = (float) settings.soundVolume();

        switch (material) {
            case ORGANIC -> {
                target.getWorld().spawnParticle(Particle.DUST, impact, count, 0.24, 0.28, 0.24, 0.015,
                        dust(Color.fromRGB(171, 30, 43), 1.15f));
                attacker.playSound(impact, Sound.ENTITY_PLAYER_ATTACK_STRONG, volume, critical ? 1.28f : 0.96f);
            }
            case GEL -> {
                target.getWorld().spawnParticle(Particle.DUST, impact, count, 0.28, 0.3, 0.28, 0.02,
                        dust(Color.fromRGB(89, 222, 134), 1.25f));
                target.getWorld().spawnParticle(Particle.GLOW, impact, Math.max(2, count / 3), 0.22, 0.24, 0.22, 0.01);
                attacker.playSound(impact, Sound.ENTITY_SLIME_HURT_SMALL, volume, critical ? 1.4f : 1.1f);
            }
            case CONSTRUCT -> {
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impact, count, 0.28, 0.3, 0.28, 0.08);
                target.getWorld().spawnParticle(Particle.DUST, impact, Math.max(3, count / 2), 0.2, 0.22, 0.2, 0.01,
                        dust(Color.fromRGB(205, 177, 118), 0.9f));
                attacker.playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_HIT, volume, critical ? 0.7f : 0.92f);
            }
            case SPECTRAL -> {
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, impact, count, 0.26, 0.3, 0.26, 0.07);
                target.getWorld().spawnParticle(Particle.DUST, impact, Math.max(3, count / 2), 0.2, 0.22, 0.2, 0.01,
                        dust(Color.fromRGB(126, 164, 220), 0.9f));
                attacker.playSound(impact, Sound.ENTITY_PLAYER_ATTACK_WEAK, volume, critical ? 1.55f : 1.22f);
            }
        }

        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, impact, Math.min(5, Math.max(2, count / 4)),
                0.2, 0.24, 0.2, 0.02);
        if (critical) {
            target.getWorld().spawnParticle(Particle.CRIT, impact, 15, 0.32, 0.34, 0.32, 0.09);
            attacker.playSound(impact, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume, 1.2f);
        }
    }

    private HitMaterial hitMaterial(MonsterDefinition definition) {
        if (definition == null) {
            return HitMaterial.ORGANIC;
        }
        String identity = (definition.id() + " " + definition.entityType() + " " + definition.modelId()).toLowerCase();
        if (identity.contains("slime") || identity.contains("magma_cube") || identity.contains("gel")
                || identity.contains("ooze") || identity.contains("jelly")) {
            return HitMaterial.GEL;
        }
        if (identity.contains("golem") || identity.contains("construct") || identity.contains("guardian")
                || identity.contains("warden") || identity.contains("automaton")) {
            return HitMaterial.CONSTRUCT;
        }
        if (identity.contains("skeleton") || identity.contains("husk") || identity.contains("zombie")
                || identity.contains("phantom") || identity.contains("wraith") || identity.contains("spirit")) {
            return HitMaterial.SPECTRAL;
        }
        return HitMaterial.ORGANIC;
    }

    private Color trailColor(WeaponArchetype archetype) {
        return switch (archetype) {
            case VANGUARD_BLADE -> Color.fromRGB(236, 205, 145);
            case RANGER_BOW -> Color.fromRGB(156, 230, 109);
            case SHADOW_DAGGER -> Color.fromRGB(220, 104, 214);
            case ARCANE_FOCUS -> Color.fromRGB(151, 100, 255);
            case WARDEN_TOTEM -> Color.fromRGB(74, 220, 184);
        };
    }

    private Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    private enum HitMaterial {
        ORGANIC,
        GEL,
        CONSTRUCT,
        SPECTRAL
    }
}
