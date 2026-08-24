package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.config.CombatCoreSettings;
import java.io.File;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class CombatCoreSettingsTest {
    private CombatCoreSettingsTest() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the combat-core.yml path");
        }
        CombatCoreSettings settings = CombatCoreSettings.from(new File(arguments[0]));
        require(settings.enabled(), "weapon combat must be enabled");
        require(settings.attack(WeaponArchetype.ARCANE_FOCUS).range() >= 16.0,
                "arcane focus must remain a ranged primary attack");
        require(settings.attack(WeaponArchetype.SHADOW_DAGGER).cooldownMillis()
                        < settings.attack(WeaponArchetype.VANGUARD_BLADE).cooldownMillis(),
                "shadow dagger must retain the faster attack cadence");
        require(settings.vanillaContent().enabled(), "vanilla content guard must be enabled");
        require(settings.vanillaContent().appliesTo("world"),
                "vanilla content guard must cover the primary world");
        require(settings.vanillaContent().appliesTo("aelorn_dungeon_01"),
                "vanilla content guard must cover authored instance worlds");
        require(settings.vanillaContent().blockNaturalCreatures(),
                "natural creatures must remain disabled");
        require(settings.vanillaContent().blockWorldLoot()
                        && settings.vanillaContent().blockBlockDrops()
                        && settings.vanillaContent().blockVanillaRecipes()
                        && settings.vanillaContent().blockFishingAndBartering()
                        && settings.vanillaContent().stripPlayerInventories(),
                "all vanilla item acquisition paths must remain disabled");
        require(settings.vanillaContent().allowedSpawnReasons().contains(CreatureSpawnEvent.SpawnReason.CUSTOM),
                "authored custom monster spawning must remain enabled");
        require(!settings.vanillaContent().allowedSpawnReasons().contains(CreatureSpawnEvent.SpawnReason.NATURAL),
                "natural creature spawning must never be whitelisted");
        require(settings.vanillaContent().allowedItemNamespaces().contains("aelorn"),
                "AELORN item namespace must be trusted");
        require(settings.vanillaContent().allowedItemNamespaces().contains("mmoitems"),
                "MMOItems namespace must be trusted");
        require(WeaponArchetype.fromClassId("arcanist").orElseThrow() == WeaponArchetype.ARCANE_FOCUS,
                "arcanist class mapping failed");
        require(WeaponArchetype.fromMmoItemsType("WAND").orElseThrow() == WeaponArchetype.ARCANE_FOCUS,
                "MMOItems wand mapping failed");
        require(WeaponArchetype.fromMmoItemsType("DAGGER").orElseThrow() == WeaponArchetype.SHADOW_DAGGER,
                "MMOItems dagger mapping failed");
        System.out.println("CombatCoreSettingsTest PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
