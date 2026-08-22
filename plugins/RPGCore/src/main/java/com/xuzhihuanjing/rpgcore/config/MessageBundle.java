package com.xuzhihuanjing.rpgcore.config;

import java.io.File;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MessageBundle {
   private final MiniMessage miniMessage = MiniMessage.miniMessage();
   private volatile YamlConfiguration messages;

   public MessageBundle(File file) {
      this.reload(file);
   }

   public void reload(File file) {
      YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
      if (loaded.getInt("schema-version", -1) != 16) {
         throw new IllegalArgumentException("Unsupported messages.yml schema-version");
      } else {
         for(String key : List.of("prefix", "loading", "load-failed", "no-console", "no-permission", "character-created", "character-selected", "character-slot-locked", "character-deletion-pending", "character-deletion-scheduled", "character-deletion-cancelled", "character-restored", "character-backup-no-slot", "slot-unavailable", "class-unavailable", "reload-complete", "reload-failed", "reload-encounter-active", "hud-enabled", "hud-disabled", "hud-refreshed", "hud-usage", "hud-status", "hud-pack-regeneration-started", "hud-pack-regeneration-busy", "ability-unavailable", "ability-no-mana", "ability-cooldown", "ability-cast", "combo-invalid", "training-weapon-restored", "identify-npc-only", "identify-batch-success", "identify-empty-selection", "identify-not-equipment", "identify-menu-full", "identify-not-enough-currency", "identify-reroll-limit", "identify-unknown-template", "identify-invalid-data", "no-active-character", "ability-tree-unavailable", "ability-tree-safe-zone", "ability-tree-unlocked", "ability-tree-already-unlocked", "ability-tree-requires-level", "ability-tree-requires-node", "ability-tree-no-points", "ability-tree-reset", "ability-tree-reset-confirm", "skill-point-added", "skill-point-removed", "skill-no-points", "skill-no-invested-points", "skill-reset", "skill-reset-confirm", "profession-xp", "profession-level-up", "monster-list-header", "monster-spawned", "monster-unknown", "monster-invalid-level", "monster-invalid-amount", "monster-invalid-location", "monster-xp", "level-up", "profile", "encounter-list-header", "encounter-started", "encounter-unknown", "encounter-invalid-level", "encounter-invalid-location", "encounter-overlap", "encounter-cooldown", "encounter-status-header", "encounter-status-entry", "encounter-none-active", "encounter-cancel-unknown", "encounter-cancel-result", "encounter-wave", "encounter-complete", "encounter-reward", "encounter-failed", "encounter-cancelled", "quest-accepted", "quest-tracked", "quest-untracked", "quest-locked", "quest-already-completed", "quest-progress", "quest-complete", "quest-complete-hud", "quest-list-header", "quest-inspect", "quest-unknown", "discovery-found", "discovery-found-hud", "discovery-list-header", "discovery-inspect", "discovery-unknown", "content-book-slot-cleared", "discovery-tracked", "discovery-hidden", "discovery-locked", "discovery-world-unavailable", "party-created", "party-invite-sent", "party-invite-received", "party-invite-declined", "party-invite-expired", "party-joined", "party-left", "party-disbanded", "party-listed", "party-unlisted", "party-member-kicked", "party-you-were-kicked", "party-leader-promoted", "party-you-are-leader", "party-not-in-party", "party-not-leader", "party-target-in-party", "party-target-not-in-party", "party-full", "party-not-found", "party-finder-disabled")) {
            if (!loaded.isString(key)) {
               throw new IllegalArgumentException("messages.yml is missing text key: " + key);
            }
         }

         this.messages = loaded;
      }
   }

   public Component message(String key, TagResolver... resolvers) {
      String prefix = this.messages.getString("prefix", "");
      String value = this.messages.getString(key, "<red>Missing message: " + key + "</red>");
      return this.miniMessage.deserialize(prefix + value, resolvers);
   }

   public Component content(String key, TagResolver... resolvers) {
      String value = this.messages.getString(key, "<red>Missing message: " + key + "</red>");
      return this.miniMessage.deserialize(value, resolvers);
   }

   public Component text(String value, TagResolver... resolvers) {
      return this.miniMessage.deserialize(value, resolvers);
   }

   public static TagResolver value(String key, String value) {
      return Placeholder.unparsed(key, value);
   }
}
