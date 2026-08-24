package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.ability.AbilityTreeService;
import tw.linsy.aelorn.rpgcore.config.AbilityTreeRegistry;
import tw.linsy.aelorn.rpgcore.config.ClassRegistry;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.ability.AbilityTreeNodeDefinition;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.classes.ArchetypeDefinition;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AbilityTreeMenuService {
   public static final int MENU_SIZE = 54;
   public static final int RESET_SLOT = 49;
   private final CharacterService characterService;
   private final AbilityTreeRegistry treeRegistry;
   private final AbilityTreeService treeService;
   private final ClassRegistry classRegistry;
   private final MessageBundle messages;

   public AbilityTreeMenuService(CharacterService characterService, AbilityTreeRegistry treeRegistry, AbilityTreeService treeService, ClassRegistry classRegistry, MessageBundle messages) {
      this.characterService = characterService;
      this.treeRegistry = treeRegistry;
      this.treeService = treeService;
      this.classRegistry = classRegistry;
      this.messages = messages;
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else if (this.treeRegistry.nodesFor(character.classId()).isEmpty()) {
         player.sendMessage(this.messages.message("ability-tree-unavailable"));
      } else {
         AbilityTreeHolder holder = new AbilityTreeHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.abilityTree());
         holder.inventory(inventory);
         int available = this.treeService.availablePoints(character);
         Material var10003 = Material.NETHER_STAR;
         String var10005 = "<gray>可用：</gray><white>" + available + "</white>";
         String var10006 = "<gray>已使用：</gray><white>" + this.treeService.spentPoints(character) + "</white>";
         int var10007 = this.treeService.earnedPoints(character);
         inventory.setItem(4, this.item(var10003, "<gold>能力點數</gold>", List.of(var10005, var10006, "<gray>累計獲得：</gray><white>" + var10007 + "</white>"), false));

         for(AbilityTreeNodeDefinition node : this.treeRegistry.nodesFor(character.classId())) {
            inventory.setItem(node.inventorySlot(), this.nodeItem(character, node));
         }

         inventory.setItem(49, this.item(Material.BARRIER, "<red>重置能力樹</red>", List.of("<gray>退還所有已使用的能力點。</gray>", "<dark_gray>只能在安全區進行。</dark_gray>", "<yellow>Shift + 左鍵確認重置</yellow>"), false));
         player.openInventory(inventory);
      }
   }

   private ItemStack nodeItem(CharacterProfile character, AbilityTreeNodeDefinition node) {
      boolean unlocked = character.unlockedAbilityNodes().contains(node.id());
      boolean prerequisitesMet = character.unlockedAbilityNodes().containsAll(node.prerequisites());
      Material material = Material.matchMaterial(node.iconMaterial());
      List<String> lore = new ArrayList();
      String var10001 = this.archetypeName(node);
      lore.add("<gray>專精：</gray>" + var10001);
      lore.add("<gray>效果：</gray><white>" + node.description() + "</white>");
      lore.add("");
      lore.add("<gray>需求等級：</gray><white>" + node.minimumLevel() + "</white>");
      lore.add("<gray>消耗點數：</gray><white>" + node.cost() + "</white>");
      if (!node.prerequisites().isEmpty()) {
         lore.add("<gray>前置節點：</gray>");
         var var10000 = node.prerequisites().stream();
         AbilityTreeRegistry var7 = this.treeRegistry;
         Objects.requireNonNull(var7);
         var10000.map(var7::find).flatMap(Optional::stream).forEach((required) -> lore.add(" <dark_gray>•</dark_gray> " + required.displayName()));
      }

      lore.add("");
      if (unlocked) {
         lore.add("<green>已解鎖</green>");
      } else if (character.level() < node.minimumLevel()) {
         lore.add("<red>角色等級不足</red>");
      } else if (!prerequisitesMet) {
         lore.add("<red>需要前置節點</red>");
      } else {
         lore.add("<yellow>左鍵解鎖</yellow>");
      }

      return this.item(material == null ? Material.STONE : material, node.displayName(), lore, unlocked);
   }

   private String archetypeName(AbilityTreeNodeDefinition node) {
      return (String)this.classRegistry.find(node.classId()).stream().map(CharacterClassDefinition::archetypes).flatMap(Collection::stream).filter((archetype) -> archetype.id().equals(node.archetypeId())).map(ArchetypeDefinition::displayName).findFirst().orElse("<gray>未知</gray>");
   }

   private ItemStack item(Material material, String name, List<String> loreLines, boolean glint) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).toList();
      meta.lore(lore);
      meta.setEnchantmentGlintOverride(glint);
      item.setItemMeta(meta);
      return item;
   }
}
