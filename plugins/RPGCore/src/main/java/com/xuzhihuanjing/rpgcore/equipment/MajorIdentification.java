package com.xuzhihuanjing.rpgcore.equipment;

import java.util.List;
import java.util.Locale;

public record MajorIdentification(String id, String displayName, List<String> description) {
   public MajorIdentification(String id, String displayName, List<String> description) {
      id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      displayName = displayName == null ? "" : displayName.trim();
      description = List.copyOf(description == null ? List.of() : description);
      this.id = id;
      this.displayName = displayName;
      this.description = description;
   }

   public static MajorIdentification empty() {
      return new MajorIdentification("", "", List.of());
   }

   public boolean enabled() {
      return !this.id.isBlank();
   }
}
