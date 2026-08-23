package tw.linsy.aelorn.mmoitems;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

final class ItemEditorService implements Listener {
   private static final int[] CONTENT = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
   private static final List<String> EDITABLE_STATS = WeaponStatCatalog.knownKeys().stream().filter((var0) -> !var0.equals("REQUIRED_LEVEL")).sorted().toList();
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;

   ItemEditorService(MMOItemsPlugin var1, MythicCoreApi var2) {
      this.plugin = var1;
      this.api = var2;
      Bukkit.getPluginManager().registerEvents(this, var1);
   }

   void openIndex(Player var1, int var2) {
      List var5 = this.plugin.serviceTemplates().stream().sorted(Comparator.comparing(ItemTemplate::type).thenComparing(ItemTemplate::id)).toList();
      int var6 = Math.max(0, (var5.size() - 1) / CONTENT.length);
      int var7 = Math.max(0, Math.min(var6, var2));
      IndexHolder var8 = new IndexHolder(var7, var5);
      Inventory var4;
      var8.inventory = var4 = Bukkit.createInventory(var8, 54, RpgGuiTitle.editor(this.plugin, "物品工坊｜模板管理"));

      int var3;
      for(int var9 = 0; var9 < CONTENT.length && (var3 = var7 * CONTENT.length + var9) < var5.size(); ++var9) {
         ItemTemplate var11 = (ItemTemplate)var5.get(var3);
         ItemStack var12 = this.plugin.serviceCreateItem(var11.type(), var11.id(), Math.max(1, var11.requiredLevel()), 1, var11.tier());
         if (var12 == null) {
            var12 = icon(var11.material(), var11.name(), List.of());
         }

         ItemMeta var10;
         ArrayList var13 = (var10 = var12.getItemMeta()).hasLore() ? new ArrayList(var10.lore()) : new ArrayList();
         var13.add(Component.empty());
         var13.add(line("左鍵：編輯", NamedTextColor.GREEN));
         var13.add(line("Shift + 右鍵：刪除", NamedTextColor.RED));
         var13.add(line(var11.type() + "." + var11.id(), NamedTextColor.DARK_GRAY));
         var10.lore(var13);
         var12.setItemMeta(var10);
         var4.setItem(CONTENT[var9], var12);
      }

      var4.setItem(45, icon(Material.BARRIER, "關閉", List.of()));
      var4.setItem(46, icon(Material.ARROW, "上一頁", List.of()));
      var4.setItem(49, icon(Material.LIME_DYE, "建立新物品", List.of(line("全程使用 GUI 建立", NamedTextColor.GRAY))));
      var4.setItem(53, icon(Material.ARROW, "下一頁", List.of()));
      var1.openInventory(var4);
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      HumanEntity var2 = var1.getWhoClicked();
      if (var2 instanceof Player var3) {
         InventoryHolder var4 = var1.getView().getTopInventory().getHolder();
         if (var4 instanceof EditorHolder var5) {
            var1.setCancelled(true);
            if (var1.getClick() == ClickType.SWAP_OFFHAND) {
               return;
            }

            if (var5 instanceof IndexHolder var6) {
               this.clickIndex(var3, var1, var6);
            } else if (var5 instanceof TypeHolder var7) {
               this.clickType(var3, var1, var7);
            } else if (var5 instanceof EditHolder var8) {
               this.clickEdit(var3, var1, var8);
            } else if (var5 instanceof MaterialHolder var9) {
               this.clickMaterial(var3, var1, var9);
            } else if (var5 instanceof StatHolder var10) {
               this.clickStats(var3, var1, var10);
            } else if (var5 instanceof ConfirmHolder var11) {
               this.clickConfirm(var3, var1, var11);
            } else if (var5 instanceof TextHolder var12) {
               this.clickText(var3, var1, var12);
            }
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof EditorHolder) {
         var1.setCancelled(true);
      }

   }

   private void clickIndex(Player var1, InventoryClickEvent var2, IndexHolder var3) {
      int var4 = var2.getRawSlot();
      if (var4 == 45) {
         var1.closeInventory();
      } else if (var4 == 46) {
         this.openIndex(var1, var3.page - 1);
      } else if (var4 == 53) {
         this.openIndex(var1, var3.page + 1);
      } else if (var4 == 49) {
         this.openTypes(var1, 0);
      } else {
         int var5 = this.contentIndex(var4);
         int var6 = var3.page * CONTENT.length + var5;
         if (var5 >= 0 && var6 < var3.templates.size()) {
            ItemTemplate var7 = (ItemTemplate)var3.templates.get(var6);
            if (var2.isShiftClick() && var2.isRightClick()) {
               this.openConfirm(var1, var7, var3.page);
            } else {
               this.openEdit(var1, var7.type(), var7.id(), var3.page);
            }
         }
      }

   }

   private void openTypes(Player var1, int var2) {
      this.openTypes(var1, "ALL", var2);
   }

   private void openTypes(Player var1, String var2, int var3) {
      List var6 = this.plugin.serviceTypeIds().stream().filter((var2x) -> var2.equals("ALL") || this.plugin.serviceCategoryId(var2x).equals(var2)).toList();
      int var7 = Math.max(0, (var6.size() - 1) / CONTENT.length);
      int var8 = Math.max(0, Math.min(var7, var3));
      TypeHolder var9 = new TypeHolder(var6, var2, var8);
      Inventory var5;
      var9.inventory = var5 = Bukkit.createInventory(var9, 54, RpgGuiTitle.editor(this.plugin, "物品工坊｜" + typeCategoryLabel(var2)));

      int var4;
      for(int var10 = 0; var10 < CONTENT.length && (var4 = var8 * CONTENT.length + var10) < var6.size(); ++var10) {
         String var11 = (String)var6.get(var4);
         var5.setItem(CONTENT[var10], icon(this.plugin.serviceTypeMaterial(var11), this.plugin.serviceTypeName(var11), List.of(line(typeCategoryLabel(this.plugin.serviceCategoryId(var11)), NamedTextColor.GRAY), line(var11, NamedTextColor.DARK_GRAY))));
      }

      var5.setItem(45, icon(Material.ARROW, "返回模板", List.of()));
      var5.setItem(46, icon(Material.ARROW, "上一頁", List.of()));
      var5.setItem(47, this.categoryIcon(Material.IRON_SWORD, "武器", "WEAPONS", var2));
      var5.setItem(48, this.categoryIcon(Material.IRON_CHESTPLATE, "裝備", "EQUIPMENT", var2));
      var5.setItem(49, this.categoryIcon(Material.COMPASS, "全部類型", "ALL", var2));
      var5.setItem(50, this.categoryIcon(Material.BUNDLE, "素材與其他", "OTHER", var2));
      var5.setItem(53, icon(Material.ARROW, "下一頁", List.of()));
      var1.openInventory(var5);
   }

   private void clickType(Player var1, InventoryClickEvent var2, TypeHolder var3) {
      if (var2.getRawSlot() == 45) {
         this.openIndex(var1, 0);
      } else if (var2.getRawSlot() == 46) {
         this.openTypes(var1, var3.category, var3.page - 1);
      } else if (var2.getRawSlot() == 53) {
         this.openTypes(var1, var3.category, var3.page + 1);
      } else if (var2.getRawSlot() == 47) {
         this.openTypes(var1, "WEAPONS", 0);
      } else if (var2.getRawSlot() == 48) {
         this.openTypes(var1, "EQUIPMENT", 0);
      } else if (var2.getRawSlot() == 49) {
         this.openTypes(var1, "ALL", 0);
      } else if (var2.getRawSlot() == 50) {
         this.openTypes(var1, "OTHER", 0);
      } else {
         int var4 = this.contentIndex(var2.getRawSlot());
         int var5 = var3.page * CONTENT.length + var4;
         if (var4 >= 0 && var5 < var3.types.size()) {
            this.openText(var1, ItemEditorService.TextAction.CREATE_ID, (String)var3.types.get(var5), "", "NEW_ITEM", 0);
         }
      }

   }

   private ItemStack categoryIcon(Material var1, String var2, String var3, String var4) {
      String var10001 = var3.equals(var4) ? "✔ " : "";
      return icon(var1, var10001 + var2, List.of(line("點擊篩選此分類", var3.equals(var4) ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
   }

   private static String typeCategoryLabel(String var0) {
      String var10000;
      switch (var0) {
         case "WEAPONS" -> var10000 = "武器";
         case "EQUIPMENT" -> var10000 = "裝備";
         case "OTHER" -> var10000 = "素材與其他";
         default -> var10000 = "全部類型";
      }

      return var10000;
   }

   private void openEdit(Player var1, String var2, String var3, int var4) {
      ItemTemplate var5 = this.plugin.serviceTemplate(var2, var3);
      if (var5 == null) {
         this.openIndex(var1, var4);
      } else {
         EditHolder var7 = new EditHolder(var2, var3, var4);
         Inventory var6;
         var7.inventory = var6 = Bukkit.createInventory(var7, 54, RpgGuiTitle.editor(this.plugin, "編輯｜" + var2 + "." + var3));
         var6.setItem(10, icon(Material.NAME_TAG, "名稱", List.of(line(var5.name(), NamedTextColor.WHITE))));
         var6.setItem(11, icon(Material.OAK_SIGN, "顯示類型", List.of(line(var5.displayedType().isBlank() ? this.plugin.serviceTypeName(var5.type()) : var5.displayedType(), NamedTextColor.GRAY))));
         var6.setItem(12, icon(var5.material(), "原版材質", List.of(line(var5.material().name(), NamedTextColor.GRAY))));
         var6.setItem(13, icon(Material.ITEM_FRAME, "物品模型", List.of(line(var5.itemModel().isBlank() ? "使用原版外觀" : var5.itemModel(), NamedTextColor.GRAY))));
         var6.setItem(14, icon(Material.NETHER_STAR, "階級", List.of(line(var5.tier(), NamedTextColor.GOLD), line("點擊切換", NamedTextColor.DARK_GRAY))));
         var6.setItem(15, icon(Material.ARMOR_STAND, "套裝", List.of(line(var5.setId().isBlank() ? "無套裝" : var5.setId(), NamedTextColor.AQUA))));
         var6.setItem(16, icon(Material.PLAYER_HEAD, "需求職業", List.of(line(var5.requiredClass().isBlank() ? "不限" : var5.requiredClass(), NamedTextColor.AQUA))));
         var6.setItem(19, icon(Material.EXPERIENCE_BOTTLE, "需求等級", List.of(line(Integer.toString(var5.requiredLevel()), NamedTextColor.GREEN), line("左右鍵 ±1，Shift ±10", NamedTextColor.DARK_GRAY))));
         var6.setItem(20, icon(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "強化模板", List.of(line(var5.upgradeTemplate(), NamedTextColor.GRAY))));
         var6.setItem(21, icon(Material.WRITABLE_BOOK, "Lore", List.of(line("使用 | 分隔多行文字", NamedTextColor.GRAY))));
         var6.setItem(22, icon(Material.MAP, "提示頁數", List.of(line(var5.tooltipPages() == 0 ? "依物品類型自動" : var5.tooltipPages() + " 頁", NamedTextColor.GRAY))));
         var6.setItem(23, icon(Material.BOOK, "背景故事", List.of(line("使用 | 分隔多行文字", NamedTextColor.GRAY))));
         var6.setItem(24, icon(var5.unbreakable() ? Material.LIME_DYE : Material.GRAY_DYE, "不會損壞：" + (var5.unbreakable() ? "是" : "否"), List.of(line("點擊切換", NamedTextColor.DARK_GRAY))));
         var6.setItem(25, icon(Material.REDSTONE, "屬性數值", List.of(line("點擊進入數值編輯", NamedTextColor.GRAY))));
         ItemTemplate.AbilityData var8 = var5.ability();
         var6.setItem(29, icon(Material.BLAZE_POWDER, "命中能力", List.of(line(var8.enabled() ? var8.type() : "無", NamedTextColor.GOLD), line("點擊切換", NamedTextColor.DARK_GRAY))));
         var6.setItem(30, icon(Material.EMERALD, "寶石孔：" + var5.gemSockets(), List.of(line("左右鍵 ±1", NamedTextColor.DARK_GRAY))));
         var6.setItem(31, icon(Material.SPECTRAL_ARROW, "能力機率：" + format(var8.chance()) + "%", List.of(line("左右鍵 ±1，Shift ±5", NamedTextColor.DARK_GRAY))));
         var6.setItem(32, icon(Material.PRISMARINE_CRYSTALS, "符文槽：" + var5.runeSlots(), List.of(line("左右鍵 ±1", NamedTextColor.DARK_GRAY))));
         var6.setItem(33, icon(Material.FIRE_CHARGE, "能力威力：" + format(var8.power()), List.of(line("左右鍵 ±1，Shift ±5", NamedTextColor.DARK_GRAY))));
         var6.setItem(34, icon(Material.KNOWLEDGE_BOOK, "Custom Model Data", List.of(line(Integer.toString(var5.customModelData()), NamedTextColor.GRAY))));
         var6.setItem(40, icon(Material.RED_DYE, "刪除物品", List.of(line("需要再次確認", NamedTextColor.RED))));
         var6.setItem(49, icon(Material.ARROW, "返回物品清單", List.of()));
         var1.openInventory(var6);
      }

   }

   private void clickEdit(Player var1, InventoryClickEvent var2, EditHolder var3) {
      ItemTemplate var4 = this.plugin.serviceTemplate(var3.type, var3.id);
      if (var4 == null) {
         this.openIndex(var1, var3.returnPage);
      } else {
         int var5 = var2.getRawSlot();
         switch (var5) {
            case 10:
               this.openText(var1, ItemEditorService.TextAction.NAME, var3.type, var3.id, plain(var4.name()), var3.returnPage);
               break;
            case 11:
               this.openText(var1, ItemEditorService.TextAction.DISPLAYED_TYPE, var3.type, var3.id, plain(var4.displayedType()), var3.returnPage);
               break;
            case 12:
               this.openMaterials(var1, var3.type, var3.id, 0, var3.returnPage);
               break;
            case 13:
               this.openText(var1, ItemEditorService.TextAction.ITEM_MODEL, var3.type, var3.id, var4.itemModel(), var3.returnPage);
               break;
            case 14:
               List var21 = this.plugin.serviceTierIds();
               int var26 = Math.max(0, var21.indexOf(var4.tier()));
               this.save(var3.type, var3.id, "tier", var21.get((var26 + 1) % var21.size()));
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 15:
               ArrayList var20 = new ArrayList();
               var20.add("");
               var20.addAll(this.plugin.serviceSetIds());
               int var25 = var20.indexOf(var4.setId());
               this.save(var3.type, var3.id, "set", var20.get(Math.floorMod(var25 + 1, var20.size())));
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 16:
               ArrayList var19 = new ArrayList();
               var19.add("");
               var19.addAll(this.api.classProfiles().keySet());
               int var24 = var19.indexOf(StatSnapshot.normalize(var4.requiredClass()));
               this.save(var3.type, var3.id, "required-class", var19.get(Math.floorMod(var24 + 1, var19.size())));
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
            case 17:
            case 18:
            case 26:
            case 27:
            case 28:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
            case 48:
            default:
               break;
            case 19:
               int var18 = var2.isShiftClick() ? 10 : 1;
               int var23 = Math.max(0, Math.min(1000, var4.requiredLevel() + (var2.isRightClick() ? -var18 : var18)));
               this.save(var3.type, var3.id, "required-level.base", var23);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 20:
               List var17 = this.plugin.serviceUpgradeIds();
               int var22 = Math.max(0, var17.indexOf(var4.upgradeTemplate()));
               this.save(var3.type, var3.id, "upgrade-template", var17.get((var22 + 1) % var17.size()));
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 21:
               this.openText(var1, ItemEditorService.TextAction.LORE, var3.type, var3.id, String.join(" | ", var4.lore()), var3.returnPage);
               break;
            case 22:
               int var16 = var4.tooltipPages() == 0 ? 3 : (var4.tooltipPages() == 3 ? 5 : 0);
               this.save(var3.type, var3.id, "tooltip-pages", var16);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 23:
               this.openText(var1, ItemEditorService.TextAction.STORY, var3.type, var3.id, String.join(" | ", var4.story()), var3.returnPage);
               break;
            case 24:
               this.save(var3.type, var3.id, "unbreakable", !var4.unbreakable());
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 25:
               this.openStats(var1, var3.type, var3.id, 0, var3.returnPage);
               break;
            case 29:
               List var15 = List.of("", "BURN", "FROST", "SHOCK", "BLEED", "HEAL");
               int var7 = Math.max(0, var15.indexOf(var4.ability().type()));
               this.save(var3.type, var3.id, "ability.on-hit.type", var15.get((var7 + 1) % var15.size()));
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 30:
               int var14 = Math.max(0, Math.min(12, var4.gemSockets() + (var2.isRightClick() ? -1 : 1)));
               this.save(var3.type, var3.id, "gem-sockets", var14);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 31:
               double var13 = var2.isShiftClick() ? (double)5.0F : (double)1.0F;
               double var27 = var2.getClick() == ClickType.MIDDLE ? (double)0.0F : Math.max((double)0.0F, Math.min((double)100.0F, var4.ability().chance() + (var2.isRightClick() ? -var13 : var13)));
               this.save(var3.type, var3.id, "ability.on-hit.chance", var27);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 32:
               int var12 = Math.max(0, Math.min(12, var4.runeSlots() + (var2.isRightClick() ? -1 : 1)));
               this.save(var3.type, var3.id, "rune-slots", var12);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 33:
               double var6 = var2.isShiftClick() ? (double)5.0F : (double)1.0F;
               double var10 = var2.getClick() == ClickType.MIDDLE ? (double)0.0F : Math.max((double)0.0F, Math.min((double)1000.0F, var4.ability().power() + (var2.isRightClick() ? -var6 : var6)));
               this.save(var3.type, var3.id, "ability.on-hit.power", var10);
               this.openEdit(var1, var3.type, var3.id, var3.returnPage);
               break;
            case 34:
               this.openText(var1, ItemEditorService.TextAction.CUSTOM_MODEL_DATA, var3.type, var3.id, Integer.toString(var4.customModelData()), var3.returnPage);
               break;
            case 40:
               this.openConfirm(var1, var4, var3.returnPage);
               break;
            case 49:
               this.openIndex(var1, var3.returnPage);
         }
      }

   }

   private void openMaterials(Player var1, String var2, String var3, int var4, int var5) {
      List var8 = Arrays.stream(Material.values()).filter((var0) -> var0 != Material.AIR && var0.isItem() && !var0.name().startsWith("LEGACY_")).sorted(Comparator.comparing(Enum::name)).toList();
      int var9 = Math.max(0, (var8.size() - 1) / CONTENT.length);
      int var10 = Math.max(0, Math.min(var9, var4));
      MaterialHolder var11 = new MaterialHolder(var2, var3, var10, var5, var8);
      Inventory var7;
      var11.inventory = var7 = Bukkit.createInventory(var11, 54, RpgGuiTitle.editor(this.plugin, "物品工坊｜選擇材質"));

      int var6;
      for(int var12 = 0; var12 < CONTENT.length && (var6 = var10 * CONTENT.length + var12) < var8.size(); ++var12) {
         Material var13 = (Material)var8.get(var6);
         var7.setItem(CONTENT[var12], icon(var13, var13.name(), List.of()));
      }

      var7.setItem(46, icon(Material.ARROW, "上一頁", List.of()));
      var7.setItem(49, icon(Material.BARRIER, "取消", List.of()));
      var7.setItem(53, icon(Material.ARROW, "下一頁", List.of()));
      var1.openInventory(var7);
   }

   private void clickMaterial(Player var1, InventoryClickEvent var2, MaterialHolder var3) {
      if (var2.getRawSlot() == 46) {
         this.openMaterials(var1, var3.type, var3.id, var3.page - 1, var3.returnPage);
      } else if (var2.getRawSlot() == 53) {
         this.openMaterials(var1, var3.type, var3.id, var3.page + 1, var3.returnPage);
      } else if (var2.getRawSlot() == 49) {
         this.openEdit(var1, var3.type, var3.id, var3.returnPage);
      } else {
         int var4 = this.contentIndex(var2.getRawSlot());
         int var5 = var3.page * CONTENT.length + var4;
         if (var4 >= 0 && var5 < var3.materials.size()) {
            this.save(var3.type, var3.id, "material", ((Material)var3.materials.get(var5)).name());
            this.openEdit(var1, var3.type, var3.id, var3.returnPage);
         }
      }

   }

   private void openStats(Player var1, String var2, String var3, int var4, int var5) {
      ItemTemplate var6 = this.plugin.serviceTemplate(var2, var3);
      if (var6 != null) {
         int var9 = Math.max(0, (EDITABLE_STATS.size() - 1) / CONTENT.length);
         int var10 = Math.max(0, Math.min(var9, var4));
         StatHolder var11 = new StatHolder(var2, var3, var10, var5);
         Inventory var8;
         var11.inventory = var8 = Bukkit.createInventory(var11, 54, RpgGuiTitle.editor(this.plugin, "物品工坊｜屬性調整"));

         int var7;
         for(int var12 = 0; var12 < CONTENT.length && (var7 = var10 * CONTENT.length + var12) < EDITABLE_STATS.size(); ++var12) {
            String var13 = (String)EDITABLE_STATS.get(var7);
            double var14 = statStep(var13);
            int var10001 = CONTENT[var12];
            Material var10002 = statIcon(var13);
            String var10003 = statDisplayName(var13);
            String var10004 = format(var6.baseStat(var13));
            var8.setItem(var10001, icon(var10002, var10003, List.of(line("目前基礎值：" + var10004 + statSuffix(var13), NamedTextColor.AQUA), line("左鍵 +" + format(var14) + "｜右鍵 -" + format(var14), NamedTextColor.GRAY), line("Shift 為 10 倍｜中鍵歸零", NamedTextColor.DARK_GRAY), line(var13, NamedTextColor.DARK_GRAY))));
         }

         var8.setItem(46, icon(Material.ARROW, "上一頁", List.of()));
         var8.setItem(49, icon(Material.ARROW, "返回", List.of()));
         var8.setItem(53, icon(Material.ARROW, "下一頁", List.of()));
         var1.openInventory(var8);
      }

   }

   private void clickStats(Player var1, InventoryClickEvent var2, StatHolder var3) {
      if (var2.getRawSlot() == 46) {
         this.openStats(var1, var3.type, var3.id, var3.page - 1, var3.returnPage);
      } else if (var2.getRawSlot() == 53) {
         this.openStats(var1, var3.type, var3.id, var3.page + 1, var3.returnPage);
      } else if (var2.getRawSlot() == 49) {
         this.openEdit(var1, var3.type, var3.id, var3.returnPage);
      } else {
         int var4 = this.contentIndex(var2.getRawSlot());
         int var5 = var3.page * CONTENT.length + var4;
         if (var4 >= 0 && var5 < EDITABLE_STATS.size()) {
            ItemTemplate var8 = this.plugin.serviceTemplate(var3.type, var3.id);
            String var9 = (String)EDITABLE_STATS.get(var5);
            double var6 = var8 == null ? (double)0.0F : var8.baseStat(var9);
            double var12 = var2.getClick() == ClickType.MIDDLE ? (double)0.0F : var6 + (var2.isRightClick() ? (double)-1.0F : (double)1.0F) * statStep(var9) * (var2.isShiftClick() ? (double)10.0F : (double)1.0F);
            this.save(var3.type, var3.id, var9.toLowerCase(Locale.ROOT).replace('_', '-') + ".base", var12);
            this.openStats(var1, var3.type, var3.id, var3.page, var3.returnPage);
         }
      }

   }

   private static Material statIcon(String var0) {
      if (!var0.contains("_DAMAGE") && !var0.contains("CRITICAL") && !var0.contains("PENETRATION") && !var0.equals("ATTACK_SPEED") && !var0.equals("RANGE") && !var0.equals("ARROW_VELOCITY") && !var0.equals("KNOCKBACK") && !var0.startsWith("BLUNT_")) {
         if (!var0.endsWith("_REDUCTION") && !var0.endsWith("_RESISTANCE") && !var0.equals("DEFENSE") && !var0.equals("ARMOR") && !var0.equals("ARMOR_TOUGHNESS") && !var0.startsWith("BLOCK_") && !var0.startsWith("DODGE_") && !var0.startsWith("PARRY_")) {
            if (var0.contains("MANA")) {
               return Material.LAPIS_LAZULI;
            } else if (!var0.contains("STAMINA") && !var0.equals("MOVEMENT_SPEED")) {
               if (!var0.contains("HEALTH") && !var0.contains("STEAL") && !var0.contains("VAMPIRISM")) {
                  return var0.equals("COOLDOWN") ? Material.CLOCK : Material.NETHER_STAR;
               } else {
                  return Material.GLISTERING_MELON_SLICE;
               }
            } else {
               return Material.FEATHER;
            }
         } else {
            return Material.SHIELD;
         }
      } else {
         return Material.IRON_SWORD;
      }
   }

   private static double statStep(String var0) {
      double var10000;
      switch (var0) {
         case "RANGE":
         case "COOLDOWN":
            var10000 = 0.1;
            break;
         default:
            var10000 = (double)1.0F;
      }

      return var10000;
   }

   private static String statSuffix(String var0) {
      return (String)WeaponStatCatalog.find(var0).map(WeaponStatCatalog.Info::suffix).orElse("");
   }

   private static String statDisplayName(String var0) {
      return (String)WeaponStatCatalog.find(var0).map(WeaponStatCatalog.Info::displayName).orElse(var0);
   }

   private void openText(Player var1, TextAction var2, String var3, String var4, String var5, int var6) {
      TextHolder var8 = new TextHolder(var2, var3, var4, var6);
      Inventory var7;
      var8.inventory = var7 = Bukkit.createInventory(var8, InventoryType.ANVIL, Component.text("輸入內容", NamedTextColor.DARK_GRAY));
      ItemStack var9 = icon(Material.PAPER, var5 != null && !var5.isBlank() ? plain(var5) : "輸入內容", List.of());
      var7.setItem(0, var9);
      var7.setItem(2, icon(Material.LIME_DYE, "確認", List.of(line("點擊完成", NamedTextColor.GREEN))));
      var1.openInventory(var7);
   }

   private void clickText(Player var1, InventoryClickEvent var2, TextHolder var3) {
      InventoryView var4;
      if (var2.getRawSlot() == 2 && (var4 = var2.getView()) instanceof AnvilView) {
         AnvilView var5 = (AnvilView)var4;
         String var6 = var5.getRenameText();
         if (var6 == null || var6.isBlank()) {
            var6 = "";
         }

         String var7 = var6.trim();
         var1.getScheduler().execute(this.plugin, () -> this.acceptText(var1, var3, var7), (Runnable)null, 1L);
      }
   }

   private void acceptText(Player var1, TextHolder var2, String var3) {
      switch (var2.action.ordinal()) {
         case 0:
            String var8 = StatSnapshot.normalize(var3);
            if (!var8.matches("[A-Z0-9_]{2,48}") || this.plugin.serviceTemplate(var2.type, var8) != null) {
               var1.sendMessage(Component.text("ID 必須為 2-48 位英文、數字或底線，且不能重複。", NamedTextColor.RED));
               this.openText(var1, ItemEditorService.TextAction.CREATE_ID, var2.type, "", var3, var2.returnPage);
               return;
            }

            this.create(var2.type, var8);
            this.openEdit(var1, var2.type, var8, var2.returnPage);
            break;
         case 1:
            this.save(var2.type, var2.id, "name", "&f" + var3);
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
            break;
         case 2:
            this.save(var2.type, var2.id, "displayed-type", var3);
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
            break;
         case 3:
            String var7 = var3.toLowerCase(Locale.ROOT);
            if (!var7.isBlank() && !var7.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
               var1.sendMessage(Component.text("模型格式必須為 namespace:path，例如 mmoitems:weapon/sword。", NamedTextColor.RED));
               this.openText(var1, ItemEditorService.TextAction.ITEM_MODEL, var2.type, var2.id, var3, var2.returnPage);
               return;
            }

            this.save(var2.type, var2.id, "item-model", var7);
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
            break;
         case 4:
            int var4;
            try {
               var4 = Math.max(0, Integer.parseInt(var3));
            } catch (NumberFormatException var6) {
               var1.sendMessage(Component.text("Custom Model Data 必須是 0 以上的整數。", NamedTextColor.RED));
               this.openText(var1, ItemEditorService.TextAction.CUSTOM_MODEL_DATA, var2.type, var2.id, var3, var2.returnPage);
               return;
            }

            this.save(var2.type, var2.id, "custom-model-data", var4);
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
            break;
         case 5:
            this.save(var2.type, var2.id, "lore", splitLines(var3));
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
            break;
         case 6:
            this.save(var2.type, var2.id, "story", splitLines(var3));
            this.openEdit(var1, var2.type, var2.id, var2.returnPage);
      }

   }

   private void openConfirm(Player var1, ItemTemplate var2, int var3) {
      ConfirmHolder var5 = new ConfirmHolder(var2.type(), var2.id(), var3);
      Inventory var4;
      var5.inventory = var4 = Bukkit.createInventory(var5, 27, Component.text("確認刪除", NamedTextColor.DARK_GRAY));
      var4.setItem(11, icon(Material.LIME_DYE, "取消", List.of()));
      var4.setItem(15, icon(Material.RED_DYE, "永久刪除 " + var2.id(), List.of(line("會先建立 YAML 備份", NamedTextColor.YELLOW))));
      var1.openInventory(var4);
   }

   private void clickConfirm(Player var1, InventoryClickEvent var2, ConfirmHolder var3) {
      if (var2.getRawSlot() == 11) {
         this.openEdit(var1, var3.type, var3.id, var3.returnPage);
      }

      if (var2.getRawSlot() == 15) {
         this.delete(var3.type, var3.id);
         var1.sendMessage(Component.text("已刪除 " + var3.type + "." + var3.id + "。", NamedTextColor.GREEN));
         this.openIndex(var1, var3.returnPage);
      }

   }

   private void create(String var1, String var2) {
      File var3 = this.fileForType(var1);
      YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var3);
      String var5 = var2 + ".base.";
      var4.set(var5 + "material", this.plugin.serviceTypeMaterial(var1).name());
      var4.set(var5 + "name", "&f" + var2);
      var4.set(var5 + "tier", "COMMON");
      var4.set(var5 + "browser-display-idx", System.currentTimeMillis() % 100000L);
      var4.set(var5 + "gem-sockets", 1);
      var4.set(var5 + "rune-slots", 1);
      var4.set(var5 + "required-level.base", 1);
      var4.set(var5 + (this.plugin.serviceIsWeaponType(var1) ? "attack-damage.base" : "defense.base"), (double)5.0F);
      var4.set(var5 + "lore", List.of("&7由 MMOItems GUI 建立的新物品。"));
      this.saveYaml(var3, var4);
   }

   private void save(String var1, String var2, String var3, Object var4) {
      File var5 = this.fileForTemplate(var1, var2);
      YamlConfiguration var6 = YamlConfiguration.loadConfiguration(var5);
      var6.set(var2 + ".base." + var3, var4);
      this.saveYaml(var5, var6);
   }

   private void delete(String var1, String var2) {
      File var3 = this.fileForTemplate(var1, var2);
      YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var3);
      if (var4.contains(var2)) {
         try {
            if (var3.isFile()) {
               Path var5 = var3.toPath();
               File var6 = var3.getParentFile();
               String var7 = var3.getName();
               Files.copy(var5, (new File(var6, var7 + ".deleted-" + Instant.now().toEpochMilli() + ".bak")).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
         } catch (IOException var8) {
            this.plugin.getLogger().warning("建立刪除備份失敗：" + var8.getMessage());
         }

         var4.set(var2, (Object)null);
         this.saveYaml(var3, var4);
      }

   }

   private void saveYaml(File var1, YamlConfiguration var2) {
      try {
         var1.getParentFile().mkdirs();
         var2.save(var1);
         this.plugin.serviceReloadItems();
      } catch (IOException var4) {
         this.plugin.getLogger().severe("物品 GUI 儲存失敗：" + var4.getMessage());
      }

   }

   private File fileForType(String var1) {
      return new File(new File(this.plugin.getDataFolder(), "items"), var1.toLowerCase(Locale.ROOT) + ".yml");
   }

   private File fileForTemplate(String var1, String var2) {
      File var4 = this.fileForType(var1);
      if (YamlConfiguration.loadConfiguration(var4).contains(var2)) {
         return var4;
      } else {
         File var5 = new File(this.plugin.getDataFolder(), "item");
         String var10003 = var1.toLowerCase(Locale.ROOT);
         File var6 = new File(var5, var10003 + ".yml");
         return YamlConfiguration.loadConfiguration(var6).contains(var2) ? var6 : var4;
      }
   }

   private int contentIndex(int var1) {
      for(int var2 = 0; var2 < CONTENT.length; ++var2) {
         if (CONTENT[var2] == var1) {
            return var2;
         }
      }

      return -1;
   }

   private static ItemStack icon(Material var0, String var1, List<Component> var2) {
      ItemStack var3 = new ItemStack(var0 != null && var0 != Material.AIR ? var0 : Material.PAPER);
      ItemMeta var4 = var3.getItemMeta();
      var4.displayName(line(plain(var1), NamedTextColor.WHITE));
      var4.lore(var2);
      var3.setItemMeta(var4);
      return var3;
   }

   private static Component line(String var0, NamedTextColor var1) {
      return Component.text(var0, var1).decoration(TextDecoration.ITALIC, false);
   }

   private static String plain(String var0) {
      return var0 == null ? "" : var0.replaceAll("(?i)[&§][0-9A-FK-ORX]", "").replaceAll("<[^>]+>", "").trim();
   }

   private static String format(double var0) {
      return String.format(Locale.ROOT, Math.abs(var0 % (double)1.0F) < 1.0E-4 ? "%.0f" : "%.2f", var0);
   }

   private static List<String> splitLines(String var0) {
      if (var0 != null && !var0.isBlank()) {
         ArrayList var1 = new ArrayList();

         for(String var5 : var0.split("\\|")) {
            String var6 = var5.trim();
            if (!var6.isBlank()) {
               var1.add("&7" + var6);
            }
         }

         return List.copyOf(var1);
      } else {
         return List.of();
      }
   }

   private static final class IndexHolder implements EditorHolder {
      private final int page;
      private final List<ItemTemplate> templates;
      private Inventory inventory;

      private IndexHolder(int var1, List<ItemTemplate> var2) {
         this.page = var1;
         this.templates = var2;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class TypeHolder implements EditorHolder {
      private final List<String> types;
      private final String category;
      private final int page;
      private Inventory inventory;

      private TypeHolder(List<String> var1, String var2, int var3) {
         this.types = var1;
         this.category = var2;
         this.page = var3;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class EditHolder implements EditorHolder {
      private final String type;
      private final String id;
      private final int returnPage;
      private Inventory inventory;

      private EditHolder(String var1, String var2, int var3) {
         this.type = var1;
         this.id = var2;
         this.returnPage = var3;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class MaterialHolder implements EditorHolder {
      private final String type;
      private final String id;
      private final int page;
      private final int returnPage;
      private final List<Material> materials;
      private Inventory inventory;

      private MaterialHolder(String var1, String var2, int var3, int var4, List<Material> var5) {
         this.type = var1;
         this.id = var2;
         this.page = var3;
         this.returnPage = var4;
         this.materials = var5;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class StatHolder implements EditorHolder {
      private final String type;
      private final String id;
      private final int page;
      private final int returnPage;
      private Inventory inventory;

      private StatHolder(String var1, String var2, int var3, int var4) {
         this.type = var1;
         this.id = var2;
         this.page = var3;
         this.returnPage = var4;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class ConfirmHolder implements EditorHolder {
      private final String type;
      private final String id;
      private final int returnPage;
      private Inventory inventory;

      private ConfirmHolder(String var1, String var2, int var3) {
         this.type = var1;
         this.id = var2;
         this.returnPage = var3;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class TextHolder implements EditorHolder {
      private final TextAction action;
      private final String type;
      private final String id;
      private final int returnPage;
      private Inventory inventory;

      private TextHolder(TextAction var1, String var2, String var3, int var4) {
         this.action = var1;
         this.type = var2;
         this.id = var3;
         this.returnPage = var4;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static enum TextAction {
      CREATE_ID,
      NAME,
      DISPLAYED_TYPE,
      ITEM_MODEL,
      CUSTOM_MODEL_DATA,
      LORE,
      STORY;

      private TextAction() {
      }
   }

   private sealed interface EditorHolder extends InventoryHolder permits ItemEditorService.IndexHolder, ItemEditorService.TypeHolder, ItemEditorService.EditHolder, ItemEditorService.MaterialHolder, ItemEditorService.StatHolder, ItemEditorService.ConfirmHolder, ItemEditorService.TextHolder {
   }
}
