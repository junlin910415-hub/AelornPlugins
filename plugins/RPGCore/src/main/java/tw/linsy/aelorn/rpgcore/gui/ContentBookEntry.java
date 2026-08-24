package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestDefinition;
import java.util.Objects;

public record ContentBookEntry(Type type, QuestDefinition quest, DiscoveryDefinition discovery) {
   public ContentBookEntry(Type type, QuestDefinition quest, DiscoveryDefinition discovery) {
      Objects.requireNonNull(type, "type");
      if (type == ContentBookEntry.Type.QUEST == (quest != null) && type == ContentBookEntry.Type.DISCOVERY == (discovery != null)) {
         this.type = type;
         this.quest = quest;
         this.discovery = discovery;
      } else {
         throw new IllegalArgumentException("Content Book entry has mismatched content");
      }
   }

   public static ContentBookEntry quest(QuestDefinition quest) {
      return new ContentBookEntry(ContentBookEntry.Type.QUEST, (QuestDefinition)Objects.requireNonNull(quest), (DiscoveryDefinition)null);
   }

   public static ContentBookEntry discovery(DiscoveryDefinition discovery) {
      return new ContentBookEntry(ContentBookEntry.Type.DISCOVERY, (QuestDefinition)null, (DiscoveryDefinition)Objects.requireNonNull(discovery));
   }

   public int minimumLevel() {
      return this.type == ContentBookEntry.Type.QUEST ? this.quest.minimumLevel() : this.discovery.minimumLevel();
   }

   public String id() {
      return this.type == ContentBookEntry.Type.QUEST ? this.quest.id() : this.discovery.id();
   }

   public static enum Type {
      QUEST,
      DISCOVERY;
   }
}
