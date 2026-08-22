package com.xuzhihuanjing.rpgcore.integration.placeholder;

import com.xuzhihuanjing.rpgcore.combat.CombatHudService;
import com.xuzhihuanjing.rpgcore.combat.CombatHudSnapshot;
import com.xuzhihuanjing.rpgcore.hud.HudNumberFormat;
import com.xuzhihuanjing.rpgcore.party.PartyService;
import com.xuzhihuanjing.rpgcore.party.PartySnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderBridge implements AutoCloseable {
   private final RpgCoreExpansion expansion;

   private PlaceholderBridge(RpgCoreExpansion expansion) {
      this.expansion = expansion;
   }

   public static PlaceholderBridge register(JavaPlugin plugin, CombatHudService hud, PartyService parties, Logger logger) {
      RpgCoreExpansion expansion = new RpgCoreExpansion(plugin, hud, parties);
      if (!expansion.register()) {
         throw new IllegalStateException("PlaceholderAPI rejected the rpgcore expansion");
      } else {
         logger.info("Optional PlaceholderAPI integration enabled for RPGCore HUD data.");
         return new PlaceholderBridge(expansion);
      }
   }

   public void close() {
      if (this.expansion.isRegistered()) {
         this.expansion.unregister();
      }

   }

   private static final class RpgCoreExpansion extends PlaceholderExpansion {
      private static final List<String> PLACEHOLDERS = List.of("%rpgcore_active%", "%rpgcore_health%", "%rpgcore_max_health%", "%rpgcore_health_compact%", "%rpgcore_max_health_compact%", "%rpgcore_health_percent%", "%rpgcore_mana%", "%rpgcore_max_mana%", "%rpgcore_mana_compact%", "%rpgcore_max_mana_compact%", "%rpgcore_mana_percent%", "%rpgcore_movement%", "%rpgcore_max_movement%", "%rpgcore_movement_percent%", "%rpgcore_movement_mode%", "%rpgcore_underwater%", "%rpgcore_level%", "%rpgcore_current_experience%", "%rpgcore_required_experience%", "%rpgcore_experience_compact%", "%rpgcore_required_experience_compact%", "%rpgcore_experience_percent%", "%rpgcore_class_id%", "%rpgcore_class_name%", "%rpgcore_world%", "%rpgcore_x%", "%rpgcore_y%", "%rpgcore_z%", "%rpgcore_coordinates%", "%rpgcore_combo_active%", "%rpgcore_combo_1%", "%rpgcore_combo_2%", "%rpgcore_combo_3%", "%rpgcore_notification%", "%rpgcore_notification_active%", "%rpgcore_party_active%", "%rpgcore_party_size%", "%rpgcore_party_max%", "%rpgcore_party_leader%", "%rpgcore_party_listed%");
      private final JavaPlugin plugin;
      private final CombatHudService hud;
      private final PartyService parties;

      private RpgCoreExpansion(JavaPlugin plugin, CombatHudService hud, PartyService parties) {
         this.plugin = (JavaPlugin)Objects.requireNonNull(plugin, "plugin");
         this.hud = (CombatHudService)Objects.requireNonNull(hud, "hud");
         this.parties = (PartyService)Objects.requireNonNull(parties, "parties");
      }

      public @NotNull String getIdentifier() {
         return "rpgcore";
      }

      public @NotNull String getAuthor() {
         return "LinSy";
      }

      public @NotNull String getVersion() {
         return this.plugin.getPluginMeta().getVersion();
      }

      public @NotNull List<String> getPlaceholders() {
         return PLACEHOLDERS;
      }

      public boolean persist() {
         return true;
      }

      public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String parameters) {
         if (player == null) {
            return "";
         } else {
            CombatHudSnapshot snapshot = this.hud.snapshot(player.getUniqueId());
            PartySnapshot party = (PartySnapshot)this.parties.partyOf(player.getUniqueId()).orElse(null);
            String var10000;
            switch (parameters.toLowerCase(Locale.ROOT)) {
               case "active" -> var10000 = bool(snapshot.active());
               case "health" -> var10000 = Integer.toString(snapshot.currentHealth());
               case "max_health" -> var10000 = Integer.toString(snapshot.maximumHealth());
               case "health_compact" -> var10000 = HudNumberFormat.compact(snapshot.currentHealth());
               case "max_health_compact" -> var10000 = HudNumberFormat.compact(snapshot.maximumHealth());
               case "health_percent" -> var10000 = percent(snapshot.healthRatio());
               case "mana" -> var10000 = Integer.toString(snapshot.currentMana());
               case "max_mana" -> var10000 = Integer.toString(snapshot.maximumMana());
               case "mana_compact" -> var10000 = HudNumberFormat.compact(snapshot.currentMana());
               case "max_mana_compact" -> var10000 = HudNumberFormat.compact(snapshot.maximumMana());
               case "mana_percent" -> var10000 = percent(snapshot.manaRatio());
               case "movement" -> var10000 = Integer.toString(snapshot.movement());
               case "max_movement" -> var10000 = Integer.toString(snapshot.maximumMovement());
               case "movement_percent" -> var10000 = percent(snapshot.movementRatio());
               case "movement_mode" -> var10000 = snapshot.movementMode().id();
               case "underwater" -> var10000 = bool(snapshot.underwater());
               case "level" -> var10000 = Integer.toString(snapshot.level());
               case "current_experience" -> var10000 = Long.toString(snapshot.currentLevelExperience());
               case "required_experience" -> var10000 = Long.toString(snapshot.requiredLevelExperience());
               case "experience_compact" -> var10000 = HudNumberFormat.compact(snapshot.currentLevelExperience());
               case "required_experience_compact" -> var10000 = HudNumberFormat.compact(snapshot.requiredLevelExperience());
               case "experience_percent" -> var10000 = percent(snapshot.experienceRatio());
               case "class_id" -> var10000 = snapshot.classId();
               case "class_name" -> var10000 = safeText(snapshot.className());
               case "world" -> var10000 = safeText(snapshot.worldName());
               case "x" -> var10000 = Integer.toString(snapshot.blockX());
               case "y" -> var10000 = Integer.toString(snapshot.blockY());
               case "z" -> var10000 = Integer.toString(snapshot.blockZ());
               case "coordinates" -> var10000 = snapshot.coordinates();
               case "combo_active" -> var10000 = bool(snapshot.comboActive());
               case "combo_1" -> var10000 = snapshot.comboToken(0);
               case "combo_2" -> var10000 = snapshot.comboToken(1);
               case "combo_3" -> var10000 = snapshot.comboToken(2);
               case "notification" -> var10000 = safeText(snapshot.notification());
               case "notification_active" -> var10000 = bool(snapshot.notificationActive());
               case "party_active" -> var10000 = bool(party != null);
               case "party_size" -> var10000 = Integer.toString(party == null ? 0 : party.members().size());
               case "party_max" -> var10000 = Integer.toString(this.parties.maximumMembers());
               case "party_leader" -> var10000 = bool(party != null && party.isLeader(player.getUniqueId()));
               case "party_listed" -> var10000 = bool(party != null && party.listed());
               default -> var10000 = null;
            }

            return var10000;
         }
      }

      private static String bool(boolean value) {
         return Boolean.toString(value);
      }

      private static String percent(double ratio) {
         return Integer.toString((int)Math.round(ratio * (double)100.0F));
      }

      private static String safeText(String value) {
         return value.replace('<', '‹').replace('>', '›');
      }
   }
}
