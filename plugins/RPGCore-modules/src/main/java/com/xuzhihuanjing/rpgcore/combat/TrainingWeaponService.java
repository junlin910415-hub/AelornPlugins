package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.classes.CharacterClassDefinition;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TrainingWeaponService {
    private final ClassRegistry classRegistry;
    private final MessageBundle messages;
    private final WeaponProfileResolver weaponProfiles;
    private final NamespacedKey markerKey;
    private final NamespacedKey characterKey;
    private final NamespacedKey classKey;

    public TrainingWeaponService(Plugin plugin, ClassRegistry classRegistry, MessageBundle messages) {
        this(plugin, classRegistry, messages, null);
    }

    public TrainingWeaponService(
            Plugin plugin,
            ClassRegistry classRegistry,
            MessageBundle messages,
            WeaponProfileResolver weaponProfiles) {
        this.classRegistry = classRegistry;
        this.messages = messages;
        this.weaponProfiles = weaponProfiles;
        this.markerKey = new NamespacedKey(plugin, "training_weapon");
        this.characterKey = new NamespacedKey(plugin, "character_id");
        this.classKey = new NamespacedKey(plugin, "class_id");
    }

    public void ensure(Player player, CharacterProfile character) {
        removeTrainingWeapons(player);
        CharacterClassDefinition definition = classRegistry.find(character.classId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown class: " + character.classId()));
        Material material = Material.matchMaterial(definition.castingMaterial());
        if (material == null) {
            throw new IllegalArgumentException("Unknown casting material: " + definition.castingMaterial());
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.text(definition.displayName() + " <white>訓練武器</white>", new TagResolver[0]));
        meta.lore(List.of(
                messages.text("<gray>武器類型：</gray><white>" + definition.weapon() + "</white>", new TagResolver[0]),
                messages.text("<gray>左鍵普攻，右鍵開始輸入三鍵技能連擊。</gray>", new TagResolver[0]),
                messages.text("<dark_gray>綁定角色：</dark_gray><white>" + character.name() + "</white>", new TagResolver[0])));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(characterKey, PersistentDataType.STRING, character.id().toString());
        data.set(classKey, PersistentDataType.STRING, character.classId());
        item.setItemMeta(meta);
        player.getInventory().addItem(item).values().forEach(leftover -> {
            Item dropped = player.getWorld().dropItem(player.getLocation(), leftover);
            dropped.setOwner(player.getUniqueId());
            dropped.setPickupDelay(0);
        });
    }

    public boolean isActiveWeapon(Player player, ItemStack item, CharacterProfile character) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        if (data.has(markerKey, PersistentDataType.BYTE)) {
            return character.id().toString().equals(data.get(characterKey, PersistentDataType.STRING))
                    && character.classId().equals(data.get(classKey, PersistentDataType.STRING));
        }
        return weaponProfiles != null && weaponProfiles.resolve(item, character).isPresent();
    }

    public Optional<WeaponProfileResolver.ResolvedWeapon> resolveActiveWeapon(
            Player player,
            ItemStack item,
            CharacterProfile character) {
        if (!isActiveWeapon(player, item, character) || weaponProfiles == null) {
            return Optional.empty();
        }
        return weaponProfiles.resolveProfile(item, character);
    }

    public boolean isTrainingWeapon(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private void removeTrainingWeapons(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            if (isTrainingWeapon(contents[index])) {
                player.getInventory().setItem(index, null);
            }
        }
    }
}
