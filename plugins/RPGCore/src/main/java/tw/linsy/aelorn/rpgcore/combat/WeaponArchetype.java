package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.domain.combat.DamageKind;
import java.util.Locale;
import java.util.Optional;

public enum WeaponArchetype {
    VANGUARD_BLADE("vanguard-blade", DamageKind.PHYSICAL),
    RANGER_BOW("ranger-bow", DamageKind.PHYSICAL),
    SHADOW_DAGGER("shadow-dagger", DamageKind.PHYSICAL),
    ARCANE_FOCUS("arcane-focus", DamageKind.MAGIC),
    WARDEN_TOTEM("warden-totem", DamageKind.MAGIC);

    private final String configKey;
    private final DamageKind damageKind;

    WeaponArchetype(String configKey, DamageKind damageKind) {
        this.configKey = configKey;
        this.damageKind = damageKind;
    }

    public String configKey() {
        return configKey;
    }

    public DamageKind damageKind() {
        return damageKind;
    }

    public static Optional<WeaponArchetype> fromClassId(String classId) {
        return switch (normalize(classId)) {
            case "vanguard", "warrior", "戰士" -> Optional.of(VANGUARD_BLADE);
            case "ranger", "archer", "弓箭手" -> Optional.of(RANGER_BOW);
            case "shadowblade", "assassin", "刺客" -> Optional.of(SHADOW_DAGGER);
            case "arcanist", "mage", "法師" -> Optional.of(ARCANE_FOCUS);
            case "warden", "shaman", "薩滿" -> Optional.of(WARDEN_TOTEM);
            default -> Optional.empty();
        };
    }

    public static Optional<WeaponArchetype> fromMmoItemsType(String itemType) {
        String type = normalize(itemType).replace('-', '_').replace(' ', '_');
        return switch (type) {
            case "sword", "long_sword", "greatsword", "great_sword", "thrusting_sword", "axe", "great_axe",
                    "greataxe", "hammer", "great_hammer", "greathammer", "spear", "lance", "halberd" ->
                    Optional.of(VANGUARD_BLADE);
            case "bow", "greatbow", "great_bow", "crossbow", "musket" -> Optional.of(RANGER_BOW);
            case "dagger", "katana", "gauntlet", "claw", "whip" -> Optional.of(SHADOW_DAGGER);
            case "wand", "staff", "greatstaff", "great_staff", "tome", "catalyst", "scepter", "focus" ->
                    Optional.of(ARCANE_FOCUS);
            case "totem", "relic", "spirit", "spirit_focus", "ritual_focus" -> Optional.of(WARDEN_TOTEM);
            default -> Optional.empty();
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
