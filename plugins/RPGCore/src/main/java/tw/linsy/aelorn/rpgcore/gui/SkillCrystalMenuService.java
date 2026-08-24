package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentBonuses;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.integration.nexo.CustomItemProvider;
import tw.linsy.aelorn.rpgcore.progression.PrimarySkillService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SkillCrystalMenuService {
   public static final int MENU_SIZE = 45;
   public static final int SUMMARY_SLOT = 4;
   public static final int RESET_SLOT = 40;
   private static final int[] SKILL_SLOTS = new int[]{20, 21, 22, 23, 24};
   private static final String SKILL_CRYSTAL_ITEM_ID = "rpgcore_skill_crystal";
   private final CharacterService characterService;
   private final PrimarySkillService primarySkillService;
   private final EquipmentService equipmentService;
   private final CustomItemProvider customItems;
   private final MessageBundle messages;

   public SkillCrystalMenuService(CharacterService characterService, PrimarySkillService primarySkillService, EquipmentService equipmentService, CustomItemProvider customItems, MessageBundle messages) {
      this.characterService = characterService;
      this.primarySkillService = primarySkillService;
      this.equipmentService = equipmentService;
      this.customItems = customItems;
      this.messages = messages;
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         SkillCrystalHolder holder = new SkillCrystalHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 45, InternalGuiTitle.skillCrystal());
         holder.inventory(inventory);
         EquipmentBonuses bonuses = this.equipmentService.bonuses(player, character);
         inventory.setItem(4, this.summaryItem(character));
         int index = 0;

         for(PrimarySkill skill : PrimarySkill.values()) {
            inventory.setItem(SKILL_SLOTS[index++], this.skillItem(character, bonuses, skill));
         }

         inventory.setItem(40, this.item(Material.BARRIER, "", "<red>重置主屬性</red>", List.of("<gray>退還所有投入的技能點。</gray>", "<dark_gray>裝備提供的點數不會被移除。</dark_gray>", "", "<yellow>Shift + 左鍵確認重置</yellow>"), false));
         player.openInventory(inventory);
      }
   }

   public PrimarySkill skillAt(int slot) {
      for(int index = 0; index < SKILL_SLOTS.length; ++index) {
         if (SKILL_SLOTS[index] == slot) {
            return PrimarySkill.values()[index];
         }
      }

      return null;
   }

   private ItemStack summaryItem(CharacterProfile character) {
      Material var10001 = Material.EMERALD;
      int var10004 = this.primarySkillService.availablePoints(character);
      String var2 = "<gray>可分配點數：</gray><green>" + var10004 + "</green>";
      int var10005 = this.primarySkillService.spentPoints(character);
      String var3 = "<gray>已投入點數：</gray><white>" + var10005 + "</white>";
      int var10006 = this.primarySkillService.earnedPoints(character);
      return this.item(var10001, "rpgcore_skill_crystal", "<green><bold>技能水晶</bold></green>", List.of(var2, var3, "<gray>累計獲得點數：</gray><white>" + var10006 + "</white>", "", "<yellow>Shift + 點擊屬性可一次調整 5 點</yellow>"), this.primarySkillService.availablePoints(character) > 0);
   }

   private ItemStack skillItem(CharacterProfile character, EquipmentBonuses bonuses, PrimarySkill skill) {
      int invested = this.primarySkillService.investedPoints(character, skill);
      int gear = bonuses.primarySkillBonus(skill);
      int total = Math.max(0, invested + gear);
      int nextInvested = Math.min(100, invested + 1);
      int nextTotal = Math.max(0, nextInvested + gear);
      String color = skill.colorTag();
      List<String> lore = new ArrayList();
      lore.add("<light_purple>提升你的 </light_purple>" + color + skill.displayName() + "</" + color.substring(1) + "<light_purple> 技能</light_purple>");
      lore.add("");
      lore.add("<gray>目前</gray><dark_gray> >>> </dark_gray><gold>下一點</gold>");
      lore.add(color + this.formatPercent(this.primarySkillService.effectPercent(skill, total)) + "%</" + color.substring(1) + "<dark_gray> >>> </dark_gray><yellow>" + this.formatPercent(this.primarySkillService.effectPercent(skill, nextTotal)) + "%</yellow>");
      lore.add("<white>" + invested + " 點</white><dark_gray> >>> </dark_gray><gold>" + nextInvested + " 點</gold>");
      String var10001 = this.signed(gear);
      lore.add("<dark_gray>* 裝備修正 " + var10001 + "</dark_gray>");
      lore.add("");
      lore.addAll(this.description(skill));
      lore.add("");
      lore.add(color + this.mainEffect(skill, total) + "</" + color.substring(1));
      lore.add("");
      lore.add("<green>左鍵增加 1 點</green>");
      lore.add("<red>右鍵移除 1 點</red>");
      lore.add("<gray>按住 Shift 改為 5 點</gray>");
      Material material = Material.matchMaterial(skill.iconMaterial());
      return this.item(material == null ? Material.EMERALD : material, "", color + "<bold>" + skill.displayName() + "</bold></" + color.substring(1), lore, invested > 0 || gear > 0);
   }

   private List<String> description(PrimarySkill skill) {
      List var10000;
      switch (skill) {
         case STRENGTH -> var10000 = List.of("<gray>每點會提高造成的所有傷害，</gray>", "<gray>並強化 </gray><green>大地</green><gray> 類型傷害。</gray>");
         case DEXTERITY -> var10000 = List.of("<gray>每點會提高暴擊機率，</gray>", "<gray>暴擊造成雙倍傷害，並強化 </gray><yellow>雷電</yellow><gray> 傷害。</gray>");
         case INTELLIGENCE -> var10000 = List.of("<gray>每點會提高最大魔力、降低技能消耗，</gray>", "<gray>並強化 </gray><aqua>流水</aqua><gray> 類型傷害。</gray>");
         case DEFENCE -> var10000 = List.of("<gray>每點會降低受到的傷害，</gray>", "<gray>並強化 </gray><red>烈焰</red><gray> 類型傷害。</gray>");
         case AGILITY -> var10000 = List.of("<gray>每點會提高閃避機率與移動能力，</gray>", "<gray>閃避時受到 10% 傷害，並強化疾風傷害。</gray>");
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String mainEffect(PrimarySkill skill, int total) {
      double percent = this.primarySkillService.effectPercent(skill, total);
      String var10000;
      switch (skill) {
         case STRENGTH -> var10000 = "總傷害提高：" + this.formatPercent(percent) + "%";
         case DEXTERITY -> var10000 = "暴擊機率：" + this.formatPercent(percent) + "%";
         case INTELLIGENCE -> var10000 = "技能消耗降低：" + this.formatPercent(this.primarySkillService.spellCostReduction(total) * (double)100.0F) + "%";
         case DEFENCE -> var10000 = "受到傷害降低：" + this.formatPercent(percent) + "%";
         case AGILITY -> var10000 = "閃避機率：" + this.formatPercent(percent) + "%";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String signed(int value) {
      return value >= 0 ? "+" + value : Integer.toString(value);
   }

   private String formatPercent(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private ItemStack item(Material material, String customItemId, String name, List<String> loreLines, boolean glint) {
      ItemStack item = customItemId != null && !customItemId.isBlank() ? (ItemStack)this.customItems.build(customItemId).orElseGet(() -> new ItemStack(material)) : new ItemStack(material);
      item.setAmount(1);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name).decoration(TextDecoration.ITALIC, false));
      var var10000 = loreLines.stream();
      MessageBundle var10001 = this.messages;
      Objects.requireNonNull(var10001);
      List<Component> lore = var10000.map((x$0) -> var10001.text(x$0)).map((line) -> line.decoration(TextDecoration.ITALIC, false)).toList();
      meta.lore(lore);
      meta.setEnchantmentGlintOverride(glint);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
      item.setItemMeta(meta);
      return item;
   }
}
