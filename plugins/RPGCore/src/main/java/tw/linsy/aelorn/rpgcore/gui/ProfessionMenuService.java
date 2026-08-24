package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionCategory;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionProgress;
import tw.linsy.aelorn.rpgcore.domain.profession.ProfessionType;
import tw.linsy.aelorn.rpgcore.hud.InternalGuiTitle;
import tw.linsy.aelorn.rpgcore.integration.nexo.CustomItemProvider;
import tw.linsy.aelorn.rpgcore.progression.ProfessionService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.List;
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

public final class ProfessionMenuService {
   public static final int MENU_SIZE = 54;
   public static final int BACK_SLOT = 49;
   private static final int[] GATHERING_SLOTS = new int[]{10, 12, 14, 16};
   private static final int[] CRAFTING_SLOTS = new int[]{28, 29, 30, 31, 32, 33, 34, 35};
   private static final String PROFESSION_ITEM_ID = "rpgcore_profession_tome";
   private final CharacterService characterService;
   private final ProfessionService professionService;
   private final CustomItemProvider customItems;
   private final MessageBundle messages;

   public ProfessionMenuService(CharacterService characterService, ProfessionService professionService, CustomItemProvider customItems, MessageBundle messages) {
      this.characterService = characterService;
      this.professionService = professionService;
      this.customItems = customItems;
      this.messages = messages;
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         ProfessionMenuHolder holder = new ProfessionMenuHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 54, InternalGuiTitle.profession());
         holder.inventory(inventory);
         inventory.setItem(4, this.overviewItem(character));
         this.renderCategory(inventory, character, ProfessionCategory.GATHERING, GATHERING_SLOTS);
         this.renderCategory(inventory, character, ProfessionCategory.CRAFTING, CRAFTING_SLOTS);
         inventory.setItem(49, this.item(Material.ARROW, "", "<yellow>返回功能選單</yellow>", List.of("<gray>回到角色中心選單。</gray>", "", "<green>左鍵返回</green>"), false));
         player.openInventory(inventory);
      }
   }

   private ItemStack overviewItem(CharacterProfile character) {
      return this.item(Material.EXPERIENCE_BOTTLE, "rpgcore_profession_tome", "<green><bold>生活技能資訊</bold></green>", List.of("<gray>生活總等級：</gray><white>" + this.professionService.totalProfessionLevel(character) + "</white>", "<gray>採集與製作會自然獲得經驗。</gray>", "<dark_gray>高等生活技能可作為後續配方、材料與區域門檻。</dark_gray>"), false);
   }

   private void renderCategory(Inventory inventory, CharacterProfile character, ProfessionCategory category, int[] slots) {
      int index = 0;

      for(ProfessionType profession : ProfessionType.values()) {
         if (profession.category() == category && index < slots.length) {
            inventory.setItem(slots[index++], this.professionItem(character, profession));
         }
      }

   }

   private ItemStack professionItem(CharacterProfile character, ProfessionType profession) {
      ProfessionProgress progress = this.professionService.progress(character, profession);
      long required = this.professionService.experienceToNextLevel(progress.level());
      List<String> lore = new ArrayList();
      lore.add("<gray>分類：</gray><white>" + profession.category().displayName() + "</white>");
      lore.add("<gray>等級：</gray><white>Lv. " + progress.level() + "</white>");
      String var10001 = this.professionService.compactProgress(progress);
      lore.add("<gray>進度：</gray><white>" + var10001 + "</white>");
      lore.add(this.progressBar(this.professionService.progressRatio(progress)));
      lore.add("");
      if (required <= 0L) {
         lore.add("<gold>已達目前最高等級</gold>");
      } else {
         long var10002 = required - progress.experience();
         lore.add("<gray>下一級還需要：</gray><gold>" + Math.max(0L, var10002) + " 經驗</gold>");
      }

      lore.add("");
      lore.addAll(this.sourceLore(profession));
      Material material = Material.matchMaterial(profession.iconMaterial());
      Material var8 = material == null ? Material.PAPER : material;
      String var10003 = profession.displayName();
      return this.item(var8, "", "<gold>" + var10003 + "</gold>", lore, progress.level() > 1);
   }

   private List<String> sourceLore(ProfessionType profession) {
      List var10000;
      switch (profession) {
         case FISHING -> var10000 = List.of("<dark_gray>來源：釣起魚類與水域戰利品。</dark_gray>");
         case WOODCUTTING -> var10000 = List.of("<dark_gray>來源：砍伐原木與巨木資源。</dark_gray>");
         case MINING -> var10000 = List.of("<dark_gray>來源：挖掘礦石、石材與晶體。</dark_gray>");
         case FARMING -> var10000 = List.of("<dark_gray>來源：收成成熟作物。</dark_gray>");
         case SCRIBING -> var10000 = List.of("<dark_gray>來源：製作書籍、地圖、卷軸材料。</dark_gray>");
         case JEWELING -> var10000 = List.of("<dark_gray>來源：製作寶石、護符、飾品材料。</dark_gray>");
         case ALCHEMY -> var10000 = List.of("<dark_gray>來源：製作藥劑與煉金原料。</dark_gray>");
         case COOKING -> var10000 = List.of("<dark_gray>來源：製作食物與料理。</dark_gray>");
         case WEAPONSMITHING -> var10000 = List.of("<dark_gray>來源：製作近戰武器與金屬武具。</dark_gray>");
         case TAILORING -> var10000 = List.of("<dark_gray>來源：製作布料、皮革與輕甲材料。</dark_gray>");
         case WOODWORKING -> var10000 = List.of("<dark_gray>來源：製作木製工具、弓弩與結構材料。</dark_gray>");
         case ARMOURING -> var10000 = List.of("<dark_gray>來源：製作盾牌、鎧甲與重甲材料。</dark_gray>");
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String progressBar(double ratio) {
      int filled = (int)Math.round(Math.max((double)0.0F, Math.min((double)1.0F, ratio)) * (double)12.0F);
      String var10000 = "|".repeat(filled);
      return "<green>" + var10000 + "</green><dark_gray>" + "|".repeat(12 - filled) + "</dark_gray>";
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
