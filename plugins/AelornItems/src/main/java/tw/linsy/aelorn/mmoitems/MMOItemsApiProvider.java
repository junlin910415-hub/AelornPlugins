package tw.linsy.aelorn.mmoitems;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tw.linsy.aelorn.mmoitems.api.MMOItemDefinition;
import tw.linsy.aelorn.mmoitems.api.MMOItemIdentity;
import tw.linsy.aelorn.mmoitems.api.MMOItemsApi;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

final class MMOItemsApiProvider implements MMOItemsApi {
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi core;

   MMOItemsApiProvider(MMOItemsPlugin var1, MythicCoreApi var2) {
      this.plugin = (MMOItemsPlugin)Objects.requireNonNull(var1, "plugin");
      this.core = (MythicCoreApi)Objects.requireNonNull(var2, "core");
   }

   public List<MMOItemDefinition> definitions() {
      return this.plugin.serviceTemplates().stream().map(this::definition).toList();
   }

   public Optional<MMOItemDefinition> definition(String var1, String var2) {
      ItemTemplate var3 = this.plugin.serviceTemplate(var1, var2);
      return Optional.ofNullable(var3).map(this::definition);
   }

   public Optional<MMOItemIdentity> inspect(ItemStack var1) {
      if (!this.isManaged(var1)) {
         return Optional.empty();
      } else {
         String var4 = this.core.readItemType(var1);
         String var5 = this.core.readItemId(var1);
         ItemTemplate var6 = this.plugin.serviceTemplate(var4, var5);
         Map var7 = this.core.readItemStats(var1);
         String var8 = this.core.readItemTag(var1, "required_class");
         if ((var8 == null || var8.isBlank()) && var6 != null) {
            var8 = var6.requiredClass();
         }

         int var3;
         if ((var3 = this.parsePositiveInt(this.core.readItemTag(var1, "required_level"))) <= 0) {
            var3 = var6 == null ? 1 : var6.requiredLevel();
         }

         Double var2;
         if ((var2 = (Double)var7.get("REQUIRED_LEVEL")) != null && Double.isFinite(var2)) {
            var3 = Math.max(1, (int)Math.round(var2));
         }

         Map var9 = var6 == null ? this.readSkillRequirements(var1) : var6.requiredSkills();
         List var10 = var6 == null ? this.splitTag(this.core.readItemTag(var1, "required_quests")) : var6.requiredQuests();
         String var11 = this.core.readItemTag(var1, "major_identification");
         if (var11.isBlank() && var6 != null) {
            var11 = var6.majorIdentification().id();
         }

         return Optional.of(new MMOItemIdentity(var4, var5, this.core.readItemTag(var1, "tier"), this.core.readItemLevel(var1), var8 == null ? "" : var8, var3, var9, var10, var11, var7));
      }
   }

   public boolean isManaged(ItemStack var1) {
      return this.plugin.serviceIsManaged(var1);
   }

   public ItemStack create(String var1, String var2, int var3, int var4, String var5) {
      return this.plugin.serviceCreateItem(var1, var2, var3, var4, var5);
   }

   public void giveOrDrop(Player var1, ItemStack var2) {
      this.plugin.serviceGiveOrDrop(var1, var2);
   }

   private MMOItemDefinition definition(ItemTemplate var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for(String var4 : var1.statKeys()) {
         double var5 = var1.baseStat(var4);
         if (Double.isFinite(var5) && Math.abs(var5) > 1.0E-6) {
            var2.put(var4, var5);
         }
      }

      return new MMOItemDefinition(var1.type(), var1.id(), var1.name(), this.plugin.serviceCategoryId(var1.type()), var1.tier(), var1.requiredClass(), var1.requiredLevel(), var1.requiredSkills(), var1.requiredQuests(), var1.majorIdentification().id(), this.plugin.serviceIsWeaponType(var1.type()), var2);
   }

   private Map<String, Integer> readSkillRequirements(ItemStack var1) {
      LinkedHashMap var2 = new LinkedHashMap();

      for(String var4 : List.of("strength", "dexterity", "intelligence", "defence", "agility", "wisdom", "vitality", "resilience")) {
         String var5 = this.core.readItemTag(var1, "required_" + var4);

         try {
            int var6 = Math.max(0, Integer.parseInt(var5));
            if (var6 > 0) {
               var2.put(var4.toUpperCase(Locale.ROOT), var6);
            }
         } catch (NumberFormatException var7) {
         }
      }

      return var2;
   }

   private List<String> splitTag(String var1) {
      return var1 != null && !var1.isBlank() ? Arrays.stream(var1.split("[,;|]+")).map(String::trim).filter((var0) -> !var0.isBlank()).toList() : List.of();
   }

   private int parsePositiveInt(String var1) {
      if (var1 != null && !var1.isBlank()) {
         try {
            return Math.max(0, (int)Math.ceil(Double.parseDouble(var1.trim())));
         } catch (NumberFormatException var3) {
            return 0;
         }
      } else {
         return 0;
      }
   }
}
