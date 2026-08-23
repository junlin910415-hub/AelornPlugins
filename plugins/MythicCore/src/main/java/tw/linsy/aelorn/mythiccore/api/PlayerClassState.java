package tw.linsy.aelorn.mythiccore.api;

import java.util.Map;

public record PlayerClassState(String classId, String characterName, int level, String stageId, String stageDisplayName, Map<String, Integer> primarySkills, String source, long updatedAtMillis) {
   public PlayerClassState(String classId, String characterName, int level, String stageId, String stageDisplayName, Map<String, Integer> primarySkills, String source, long updatedAtMillis) {
      classId = StatSnapshot.normalize(classId);
      characterName = characterName == null ? "" : characterName.trim();
      level = Math.max(1, level);
      stageId = StatSnapshot.normalize(stageId);
      stageDisplayName = stageDisplayName == null ? "" : stageDisplayName.trim();
      primarySkills = Map.copyOf(primarySkills == null ? Map.of() : primarySkills);
      source = source == null ? "" : source.trim();
      updatedAtMillis = Math.max(0L, updatedAtMillis);
      this.classId = classId;
      this.characterName = characterName;
      this.level = level;
      this.stageId = stageId;
      this.stageDisplayName = stageDisplayName;
      this.primarySkills = primarySkills;
      this.source = source;
      this.updatedAtMillis = updatedAtMillis;
   }
}
