package tw.linsy.aelorn.rpgcore.persistence;

import tw.linsy.aelorn.rpgcore.domain.character.AccountProfile;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterRepository;
import tw.linsy.aelorn.rpgcore.domain.character.DeletedCharacterBackup;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionProgress;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestProgress;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlCharacterRepository implements CharacterRepository {
   private static final int SCHEMA_VERSION = 5;
   private final Path directory;

   public YamlCharacterRepository(Path directory) throws IOException {
      this.directory = directory.toAbsolutePath().normalize();
      Files.createDirectories(this.directory);
   }

   public Optional<AccountProfile> find(UUID ownerId) throws IOException {
      Path path = this.fileFor(ownerId);
      if (!Files.exists(path, new LinkOption[0])) {
         return Optional.empty();
      } else {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
         int schemaVersion = yaml.getInt("schema-version", -1);
         if (schemaVersion >= 1 && schemaVersion <= 5) {
            Map<Integer, CharacterProfile> characters = new LinkedHashMap();
            ConfigurationSection section = yaml.getConfigurationSection("characters");
            if (section != null) {
               for(String slotKey : section.getKeys(false)) {
                  int slot = this.parseSlot(slotKey, ownerId);
                  characters.put(slot, this.readCharacter(yaml, "characters." + slotKey + ".", slot));
               }
            }

            Map<Integer, Instant> pendingDeletions = new LinkedHashMap();
            ConfigurationSection deletionSection = yaml.getConfigurationSection("pending-deletions");
            if (deletionSection != null) {
               for(String slotKey : deletionSection.getKeys(false)) {
                  int slot = this.parseSlot(slotKey, ownerId);
                  if (characters.containsKey(slot)) {
                     pendingDeletions.put(slot, Instant.ofEpochMilli(Math.max(0L, deletionSection.getLong(slotKey))));
                  }
               }
            }

            List<DeletedCharacterBackup> backups = new ArrayList();
            ConfigurationSection backupSection = yaml.getConfigurationSection("backups");
            if (backupSection != null) {
               for(String key : backupSection.getKeys(false).stream().sorted(Comparator.comparingInt((keyx) -> this.parseIndex(keyx, ownerId))).toList()) {
                  String prefix = "backups." + key + ".";
                  int originalSlot = yaml.getInt(prefix + "character.slot", 0);
                  backups.add(new DeletedCharacterBackup(this.readCharacter(yaml, prefix + "character.", originalSlot), Instant.ofEpochMilli(Math.max(0L, yaml.getLong(prefix + "deleted-at")))));
               }
            }

            return Optional.of(new AccountProfile(ownerId, yaml.getInt("active-slot", -1), characters, yaml.getBoolean("preferences.selector-music", true), yaml.getBoolean("preferences.auto-open-selector", false), pendingDeletions, backups));
         } else {
            throw new IOException("Unsupported player data schema for " + String.valueOf(ownerId));
         }
      }
   }

   public void save(AccountProfile account) throws IOException {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("schema-version", 5);
      yaml.set("owner", account.ownerId().toString());
      yaml.set("active-slot", account.activeSlot());
      yaml.set("preferences.selector-music", account.selectorMusicEnabled());
      yaml.set("preferences.auto-open-selector", account.autoOpenSelector());
      account.characters().forEach((slot, character) -> this.writeCharacter(yaml, "characters." + slot + ".", character, false));
      account.pendingDeletions().forEach((slot, deleteAt) -> yaml.set("pending-deletions." + slot, deleteAt.toEpochMilli()));

      for(int index = 0; index < account.backups().size(); ++index) {
         DeletedCharacterBackup backup = (DeletedCharacterBackup)account.backups().get(index);
         String prefix = "backups." + index + ".";
         yaml.set(prefix + "deleted-at", backup.deletedAt().toEpochMilli());
         this.writeCharacter(yaml, prefix + "character.", backup.character(), true);
      }

      Path destination = this.fileFor(account.ownerId());
      Path temporary = this.directory.resolve(String.valueOf(account.ownerId()) + ".yml.tmp");
      yaml.save(temporary.toFile());

      try {
         Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var6) {
         Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }

   }

   private CharacterProfile readCharacter(YamlConfiguration yaml, String prefix, int slot) throws IOException {
      return new CharacterProfile(UUID.fromString(requireString(yaml, prefix + "id")), slot, requireString(yaml, prefix + "name"), requireString(yaml, prefix + "class"), yaml.getInt(prefix + "level", 1), yaml.getLong(prefix + "experience", 0L), Set.copyOf(yaml.getStringList(prefix + "ability-nodes")), this.readSkillPoints(yaml, prefix), this.readProfessions(yaml, prefix), this.readQuestProgress(yaml, prefix), yaml.getString(prefix + "tracked-quest", ""), Set.copyOf(yaml.getStringList(prefix + "discoveries")), Math.max(0L, yaml.getLong(prefix + "play-time-seconds", 0L)), Instant.ofEpochMilli(yaml.getLong(prefix + "created-at")), Instant.ofEpochMilli(yaml.getLong(prefix + "last-played-at")));
   }

   private void writeCharacter(YamlConfiguration yaml, String prefix, CharacterProfile character, boolean includeSlot) {
      if (includeSlot) {
         yaml.set(prefix + "slot", character.slot());
      }

      yaml.set(prefix + "id", character.id().toString());
      yaml.set(prefix + "name", character.name());
      yaml.set(prefix + "class", character.classId());
      yaml.set(prefix + "level", character.level());
      yaml.set(prefix + "experience", character.experience());
      yaml.set(prefix + "ability-nodes", character.unlockedAbilityNodes().stream().sorted().toList());
      character.skillPoints().forEach((skill, points) -> yaml.set(prefix + "skill-points." + skill.id(), points));
      character.professions().forEach((profession, progress) -> {
         String professionPrefix = prefix + "professions." + profession.id() + ".";
         yaml.set(professionPrefix + "level", progress.level());
         yaml.set(professionPrefix + "experience", progress.experience());
      });
      yaml.set(prefix + "tracked-quest", character.trackedQuestId());
      yaml.set(prefix + "discoveries", character.discoveredLocations().stream().sorted().toList());
      character.questProgress().forEach((questId, progress) -> {
         String questPrefix = prefix + "quests." + questId + ".";
         yaml.set(questPrefix + "status", progress.status().name());
         progress.objectiveProgress().forEach((objective, amount) -> yaml.set(questPrefix + "objectives." + objective, amount));
      });
      yaml.set(prefix + "play-time-seconds", character.playTimeSeconds());
      yaml.set(prefix + "created-at", character.createdAt().toEpochMilli());
      yaml.set(prefix + "last-played-at", character.lastPlayedAt().toEpochMilli());
   }

   private Map<PrimarySkill, Integer> readSkillPoints(YamlConfiguration yaml, String prefix) {
      EnumMap<PrimarySkill, Integer> result = new EnumMap(PrimarySkill.class);

      for(PrimarySkill skill : PrimarySkill.values()) {
         result.put(skill, Math.max(0, yaml.getInt(prefix + "skill-points." + skill.id(), 0)));
      }

      return result;
   }

   private Map<ProfessionType, ProfessionProgress> readProfessions(YamlConfiguration yaml, String prefix) {
      EnumMap<ProfessionType, ProfessionProgress> result = new EnumMap(ProfessionType.class);

      for(ProfessionType profession : ProfessionType.values()) {
         String professionPrefix = prefix + "professions." + profession.id() + ".";
         result.put(profession, new ProfessionProgress(Math.max(1, yaml.getInt(professionPrefix + "level", 1)), Math.max(0L, yaml.getLong(professionPrefix + "experience", 0L))));
      }

      return result;
   }

   private Map<String, QuestProgress> readQuestProgress(YamlConfiguration yaml, String prefix) throws IOException {
      Map<String, QuestProgress> result = new LinkedHashMap();
      ConfigurationSection quests = yaml.getConfigurationSection(prefix + "quests");
      if (quests == null) {
         return result;
      } else {
         for(String questId : quests.getKeys(false)) {
            String questPrefix = prefix + "quests." + questId + ".";

            QuestStatus status;
            try {
               status = QuestStatus.valueOf(requireString(yaml, questPrefix + "status"));
            } catch (IllegalArgumentException exception) {
               throw new IOException("Invalid quest status for " + questId, exception);
            }

            Map<String, Integer> objectives = new LinkedHashMap();
            ConfigurationSection objectiveSection = yaml.getConfigurationSection(questPrefix + "objectives");
            if (objectiveSection != null) {
               for(String objectiveId : objectiveSection.getKeys(false)) {
                  objectives.put(objectiveId, Math.max(0, objectiveSection.getInt(objectiveId)));
               }
            }

            result.put(questId, new QuestProgress(status, objectives));
         }

         return result;
      }
   }

   private int parseSlot(String value, UUID ownerId) throws IOException {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException exception) {
         throw new IOException("Invalid character slot " + value + " for " + String.valueOf(ownerId), exception);
      }
   }

   private int parseIndex(String value, UUID ownerId) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException exception) {
         throw new IllegalArgumentException("Invalid backup index " + value + " for " + String.valueOf(ownerId), exception);
      }
   }

   private Path fileFor(UUID ownerId) throws IOException {
      Path path = this.directory.resolve(String.valueOf(ownerId) + ".yml").normalize();
      if (!path.startsWith(this.directory)) {
         throw new IOException("Player data path escaped storage directory");
      } else {
         return path;
      }
   }

   private static String requireString(YamlConfiguration yaml, String path) throws IOException {
      String value = yaml.getString(path);
      if (value != null && !value.isBlank()) {
         return value;
      } else {
         throw new IOException("Missing required player data field: " + path);
      }
   }
}
