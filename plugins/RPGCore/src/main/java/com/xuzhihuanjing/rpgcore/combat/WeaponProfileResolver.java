package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import com.xuzhihuanjing.rpgcore.item.CustomItemMarker;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class WeaponProfileResolver {
    private static final Set<String> VANGUARD_ALIASES = Set.of("vanguard", "warrior", "戰士");
    private static final Set<String> RANGER_ALIASES = Set.of("ranger", "archer", "弓箭手");
    private static final Set<String> SHADOW_ALIASES = Set.of("shadowblade", "assassin", "刺客");
    private static final Set<String> ARCANE_ALIASES = Set.of("arcanist", "mage", "法師");
    private static final Set<String> WARDEN_ALIASES = Set.of("warden", "shaman", "薩滿");

    private final MmoItemsBridge mmoItems;
    private final CustomItemMarker customItems;

    public WeaponProfileResolver(MmoItemsBridge mmoItems, CustomItemMarker customItems) {
        this.mmoItems = mmoItems;
        this.customItems = customItems;
    }

    public Optional<WeaponArchetype> resolve(ItemStack item, CharacterProfile character) {
        return resolveProfile(item, character).map(ResolvedWeapon::archetype);
    }

    public Optional<ResolvedWeapon> resolveProfile(ItemStack item, CharacterProfile character) {
        if (item == null || item.getType().isAir() || character == null) {
            return Optional.empty();
        }
        WeaponArchetype expected = WeaponArchetype.fromClassId(character.classId()).orElse(null);
        if (expected == null) {
            return Optional.empty();
        }

        MmoItemsBridge.Identity identity = mmoItems.inspect(item).orElse(null);
        if (identity != null) {
            if (!identity.requiredClass().isBlank() && !matchesClass(identity.requiredClass(), expected)) {
                return Optional.empty();
            }
            WeaponArchetype declared = WeaponArchetype.fromMmoItemsType(identity.type()).orElse(null);
            if (declared != expected) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedWeapon(
                    expected,
                    cadenceForType(identity.type(), expected),
                    attackSpeedRating(identity)));
        }

        if (!customItems.isCustom(item)) {
            return Optional.empty();
        }
        WeaponArchetype materialStyle = classifyMaterial(item.getType()).orElse(expected);
        return materialStyle == expected
                ? Optional.of(new ResolvedWeapon(expected, expected.configKey(), 0.0))
                : Optional.empty();
    }

    private boolean matchesClass(String requiredClass, WeaponArchetype expected) {
        String normalized = requiredClass.trim().toLowerCase(Locale.ROOT);
        return switch (expected) {
            case VANGUARD_BLADE -> VANGUARD_ALIASES.contains(normalized);
            case RANGER_BOW -> RANGER_ALIASES.contains(normalized);
            case SHADOW_DAGGER -> SHADOW_ALIASES.contains(normalized);
            case ARCANE_FOCUS -> ARCANE_ALIASES.contains(normalized);
            case WARDEN_TOTEM -> WARDEN_ALIASES.contains(normalized);
        };
    }

    private Optional<WeaponArchetype> classifyMaterial(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("TRIDENT") || name.equals("MACE")) {
            return Optional.of(WeaponArchetype.VANGUARD_BLADE);
        }
        if (material == Material.BOW || material == Material.CROSSBOW) {
            return Optional.of(WeaponArchetype.RANGER_BOW);
        }
        if (material == Material.SHEARS) {
            return Optional.of(WeaponArchetype.SHADOW_DAGGER);
        }
        if (material == Material.BLAZE_ROD || material == Material.STICK) {
            return Optional.of(WeaponArchetype.ARCANE_FOCUS);
        }
        if (material == Material.HEART_OF_THE_SEA || material == Material.NAUTILUS_SHELL) {
            return Optional.of(WeaponArchetype.WARDEN_TOTEM);
        }
        return Optional.empty();
    }

    private String cadenceForType(String itemType, WeaponArchetype fallback) {
        String type = itemType == null ? ""
                : itemType.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (type) {
            case "greatsword", "great_sword", "great_axe", "greataxe", "hammer",
                    "great_hammer", "greathammer" -> "great-weapon";
            case "spear", "lance", "halberd", "thrusting_sword" -> "polearm";
            case "gauntlet", "claw", "whip" -> "gauntlet";
            case "staff", "greatstaff", "great_staff" -> "arcane-staff";
            default -> fallback.configKey();
        };
    }

    private double attackSpeedRating(MmoItemsBridge.Identity identity) {
        for (var entry : identity.stats().entrySet()) {
            String key = entry.getKey().replace('-', '_').replace(' ', '_');
            if (key.equalsIgnoreCase("ATTACK_SPEED") && Double.isFinite(entry.getValue())) {
                return Math.max(0.0, entry.getValue());
            }
        }
        return 0.0;
    }

    public record ResolvedWeapon(
            WeaponArchetype archetype,
            String cadenceProfileId,
            double attackSpeedRating) {
    }
}
