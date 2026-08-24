package tw.linsy.aelorn.rpgcore.discovery;

import tw.linsy.aelorn.rpgcore.combat.CharacterActivationService;
import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.config.DiscoveryRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestProgress;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class DiscoveryService {
   private final DiscoveryRegistry registry;
   private final CharacterService characterService;
   private final ProgressionService progressionService;
   private final CharacterActivationService activationService;
   private final QuestService questService;
   private final HudNotificationService notifications;
   private final MessageBundle messages;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public DiscoveryService(DiscoveryRegistry registry, CharacterService characterService, ProgressionService progressionService, CharacterActivationService activationService, QuestService questService, HudNotificationService notifications, MessageBundle messages) {
      this.registry = (DiscoveryRegistry)Objects.requireNonNull(registry, "registry");
      this.characterService = (CharacterService)Objects.requireNonNull(characterService, "characterService");
      this.progressionService = (ProgressionService)Objects.requireNonNull(progressionService, "progressionService");
      this.activationService = (CharacterActivationService)Objects.requireNonNull(activationService, "activationService");
      this.questService = (QuestService)Objects.requireNonNull(questService, "questService");
      this.notifications = (HudNotificationService)Objects.requireNonNull(notifications, "notifications");
      this.messages = (MessageBundle)Objects.requireNonNull(messages, "messages");
   }

   public Availability availability(CharacterProfile character, DiscoveryDefinition discovery) {
      return evaluateAvailability(character, discovery);
   }

   public static Availability evaluateAvailability(CharacterProfile character, DiscoveryDefinition discovery) {
      if (character.discoveredLocations().contains(discovery.id())) {
         return DiscoveryService.Availability.DISCOVERED;
      } else {
         return character.level() >= discovery.minimumLevel() && character.discoveredLocations().containsAll(discovery.prerequisites()) && questsComplete(character, discovery.requiredQuests()) ? DiscoveryService.Availability.AVAILABLE : DiscoveryService.Availability.LOCKED;
      }
   }

   public int inspectLocation(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      Location location = player.getLocation();
      if (character != null && location.getWorld() != null) {
         int discovered = 0;

         for(DiscoveryDefinition definition : this.registry.candidates(location.getWorld().getName(), location.getX(), location.getZ())) {
            if (this.inside(location, definition) && this.discover(player, definition)) {
               ++discovered;
            }
         }

         return discovered;
      } else {
         return 0;
      }
   }

   private boolean discover(Player player, DiscoveryDefinition discovery) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character != null && this.availability(character, discovery) == DiscoveryService.Availability.AVAILABLE) {
         Set<String> updated = new LinkedHashSet(character.discoveredLocations());
         if (!updated.add(discovery.id())) {
            return false;
         } else {
            this.characterService.updateActiveCharacter(player.getUniqueId(), (current) -> current.withDiscoveredLocations(updated));
            ProgressionResult result = this.progressionService.grantExperience(player.getUniqueId(), discovery.rewardExperience());
            String name = this.miniMessage.stripTags(discovery.displayName());
            this.notifications.show(player.getUniqueId(), this.messages.content("discovery-found-hud", MessageBundle.value("discovery", name), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));
            player.sendMessage(this.messages.message("discovery-found", MessageBundle.value("discovery", name), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));
            this.questService.recordDiscovery(player, discovery.id());
            if (result.leveledUp()) {
               player.sendMessage(this.messages.message("level-up", MessageBundle.value("level", Integer.toString(result.level()))));
               this.characterService.activeCharacter(player.getUniqueId()).ifPresent((current) -> this.activationService.activate(player, current));
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private boolean inside(Location location, DiscoveryDefinition discovery) {
      if (location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(discovery.world())) {
         double dx = location.getX() - discovery.x();
         double dy = location.getY() - discovery.y();
         double dz = location.getZ() - discovery.z();
         return dx * dx + dy * dy + dz * dz <= discovery.radius() * discovery.radius();
      } else {
         return false;
      }
   }

   private static boolean questsComplete(CharacterProfile character, List<String> questIds) {
      for(String id : questIds) {
         QuestProgress progress = (QuestProgress)character.questProgress().get(id);
         if (progress == null || progress.status() != QuestStatus.COMPLETED) {
            return false;
         }
      }

      return true;
   }

   public static enum Availability {
      LOCKED,
      AVAILABLE,
      DISCOVERED;
   }
}
