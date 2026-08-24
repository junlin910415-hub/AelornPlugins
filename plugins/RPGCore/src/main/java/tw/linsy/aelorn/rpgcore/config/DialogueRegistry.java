package tw.linsy.aelorn.rpgcore.config;

import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueChoice;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueDefinition;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueLine;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueNode;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 讀取 dialogues.yml。
 *
 * <p>與其他 registry 一樣採「全部驗證通過才套用」策略:任何一段對話有錯就整份拒絕載入,
 * 而不是靜默跳過壞掉的那段——半套的劇情比完全沒有更難察覺。
 */
public final class DialogueRegistry {

    private static final int SCHEMA_VERSION = 1;

    private volatile Map<String, DialogueDefinition> dialogues = Map.of();
    /** NPC id(小寫)對應到該 NPC 的對話,供互動時 O(1) 查找。 */
    private volatile Map<String, DialogueDefinition> byNpc = Map.of();

    public void load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("schema-version", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported dialogues.yml schema-version (expected "
                + SCHEMA_VERSION + ")");
        }

        ConfigurationSection root = yaml.getConfigurationSection("dialogues");
        if (root == null || root.getKeys(false).isEmpty()) {
            this.dialogues = Map.of();
            this.byNpc = Map.of();
            return;
        }

        Map<String, DialogueDefinition> loaded = new LinkedHashMap<>();
        Map<String, DialogueDefinition> npcIndex = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                errors.add("Invalid dialogue section: " + id);
                continue;
            }
            DialogueDefinition definition = readDefinition(id, section, errors);
            if (definition == null) {
                continue;
            }
            validate(definition, errors);
            if (loaded.putIfAbsent(id, definition) != null) {
                errors.add("Duplicate dialogue id: " + id);
            }
            String npcKey = definition.npcId().toLowerCase(Locale.ROOT);
            DialogueDefinition clash = npcIndex.putIfAbsent(npcKey, definition);
            if (clash != null) {
                errors.add("NPC " + definition.npcId() + " already has dialogue " + clash.id()
                    + "; " + id + " would shadow it");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
        }
        this.dialogues = Collections.unmodifiableMap(loaded);
        this.byNpc = Collections.unmodifiableMap(npcIndex);
    }

    private DialogueDefinition readDefinition(String id, ConfigurationSection section, List<String> errors) {
        String npcId = section.getString("npc", "");
        if (npcId == null || npcId.isBlank()) {
            errors.add("Dialogue " + id + " is missing npc");
            return null;
        }
        String startNode = section.getString("start", "");
        if (startNode == null || startNode.isBlank()) {
            errors.add("Dialogue " + id + " is missing start");
            return null;
        }

        ConfigurationSection nodesSection = section.getConfigurationSection("nodes");
        if (nodesSection == null || nodesSection.getKeys(false).isEmpty()) {
            errors.add("Dialogue " + id + " has no nodes");
            return null;
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        for (String nodeId : nodesSection.getKeys(false)) {
            ConfigurationSection nodeSection = nodesSection.getConfigurationSection(nodeId);
            if (nodeSection == null) {
                errors.add("Dialogue " + id + " has invalid node " + nodeId);
                continue;
            }
            nodes.put(nodeId, readNode(id, nodeId, nodeSection, errors));
        }

        String requiredQuest = section.getString("required-quest");
        return new DialogueDefinition(
            id,
            npcId,
            section.getString("speaker", ""),
            startNode,
            nodes,
            section.getBoolean("lock-movement", true),
            section.getBoolean("skippable", true),
            requiredQuest == null || requiredQuest.isBlank() ? null : requiredQuest);
    }

    private DialogueNode readNode(String dialogueId, String nodeId, ConfigurationSection section,
                                  List<String> errors) {
        List<DialogueLine> lines = new ArrayList<>();
        List<?> rawLines = section.getList("lines");
        if (rawLines != null) {
            for (Object raw : rawLines) {
                DialogueLine line = readLine(raw);
                if (line == null) {
                    errors.add("Dialogue " + dialogueId + " node " + nodeId + " has an invalid line entry");
                } else {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            errors.add("Dialogue " + dialogueId + " node " + nodeId + " has no lines");
        }

        List<DialogueChoice> choices = new ArrayList<>();
        List<Map<?, ?>> rawChoices = section.getMapList("choices");
        for (Map<?, ?> raw : rawChoices) {
            String text = string(raw.get("text"));
            String next = string(raw.get("next"));
            if (text == null || next == null) {
                errors.add("Dialogue " + dialogueId + " node " + nodeId + " has a choice missing text or next");
                continue;
            }
            choices.add(new DialogueChoice(
                text,
                next,
                raw.get("minimum-level") instanceof Number level ? level.intValue() : 0,
                string(raw.get("required-quest")),
                Boolean.TRUE.equals(raw.get("hidden-when-locked"))));
        }

        String next = section.getString("next");
        return new DialogueNode(
            nodeId,
            lines,
            choices,
            next == null || next.isBlank() ? null : next,
            section.getBoolean("grant-quest-progress", false));
    }

    /** 台詞可以是純字串(常見情況)或帶選項的 map。 */
    private DialogueLine readLine(Object raw) {
        if (raw instanceof String text) {
            return new DialogueLine(text, null, false, 0);
        }
        if (raw instanceof Map<?, ?> map) {
            String text = string(map.get("text"));
            if (text == null) {
                return null;
            }
            return new DialogueLine(
                text,
                string(map.get("speaker")),
                Boolean.TRUE.equals(map.get("typewriter")),
                map.get("pause") instanceof Number pause ? pause.intValue() : 0);
        }
        return null;
    }

    /** 節點指向必須存在,否則對話會在執行期斷掉。 */
    private void validate(DialogueDefinition definition, List<String> errors) {
        if (definition.start() == null) {
            errors.add("Dialogue " + definition.id() + " start node " + definition.startNode() + " does not exist");
        }
        for (DialogueNode node : definition.nodes().values()) {
            if (node.next() != null && definition.node(node.next()) == null) {
                errors.add("Dialogue " + definition.id() + " node " + node.id()
                    + " points to unknown node " + node.next());
            }
            for (DialogueChoice choice : node.choices()) {
                if (definition.node(choice.next()) == null) {
                    errors.add("Dialogue " + definition.id() + " node " + node.id()
                        + " has a choice pointing to unknown node " + choice.next());
                }
            }
        }
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    public Optional<DialogueDefinition> find(String id) {
        return Optional.ofNullable(dialogues.get(id));
    }

    /** 互動路徑用的查找;NPC id 大小寫不敏感。 */
    public Optional<DialogueDefinition> forNpc(String npcId) {
        return npcId == null ? Optional.empty()
            : Optional.ofNullable(byNpc.get(npcId.toLowerCase(Locale.ROOT)));
    }

    public Collection<DialogueDefinition> all() {
        return dialogues.values();
    }

    public int size() {
        return dialogues.size();
    }
}
