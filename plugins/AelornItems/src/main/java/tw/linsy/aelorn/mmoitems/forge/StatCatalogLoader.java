package tw.linsy.aelorn.mmoitems.forge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public final class StatCatalogLoader {
   private static final String ELEMENT_TOKEN = "{element}";
   private final Map<String, Entry> stats = new LinkedHashMap();
   private final Map<String, Category> categories = new LinkedHashMap();
   private final Map<String, String> aliases = new LinkedHashMap();
   private final Set<String> metadataKeys = new LinkedHashSet();
   private final Map<String, Element> elements = new LinkedHashMap();
   private final Set<String> loreHidden = new LinkedHashSet();

   public StatCatalogLoader() {
   }

   public int load(ConfigurationSection var1) {
      this.stats.clear();
      this.categories.clear();
      this.aliases.clear();
      this.metadataKeys.clear();
      this.elements.clear();
      this.loreHidden.clear();
      if (var1 == null) {
         return 0;
      } else {
         this.loadCategories(var1.getConfigurationSection("categories"));
         this.loadElements(var1.getConfigurationSection("elements"));
         this.loadStats(var1.getConfigurationSection("stats"));
         this.loadElementTemplates(var1.getConfigurationSection("element-templates"));
         this.loadAliases(var1.getConfigurationSection("aliases"));
         var1.getStringList("metadata-keys").forEach((var1x) -> this.metadataKeys.add(normalize(var1x)));
         ConfigurationSection var2 = var1.getConfigurationSection("lore");
         if (var2 != null) {
            var2.getStringList("hidden").forEach((var1x) -> this.loreHidden.add(normalize(var1x)));
         }

         return this.stats.size();
      }
   }

   private void loadCategories(ConfigurationSection var1) {
      if (var1 != null) {
         for(String var3 : var1.getKeys(false)) {
            ConfigurationSection var4 = var1.getConfigurationSection(var3);
            if (var4 != null) {
               String var5 = normalize(var3);
               this.categories.put(var5, new Category(var5, var4.getString("name", var5), var4.getString("color", "&7"), var4.getString("icon", ""), var4.getInt("order", 100)));
            }
         }

      }
   }

   private void loadElements(ConfigurationSection var1) {
      if (var1 != null) {
         for(String var3 : var1.getKeys(false)) {
            ConfigurationSection var4 = var1.getConfigurationSection(var3);
            String var5 = normalize(var3);
            if (var4 == null) {
               this.elements.put(var5, new Element(var5, var5, "&7"));
            } else {
               this.elements.put(var5, new Element(var5, var4.getString("name", var5), var4.getString("color", "&7")));
            }
         }

      }
   }

   private void loadStats(ConfigurationSection var1) {
      if (var1 != null) {
         for(String var3 : var1.getKeys(false)) {
            ConfigurationSection var4 = var1.getConfigurationSection(var3);
            if (var4 != null) {
               this.put(normalize(var3), var4.getString("name", var3), var4.getString("suffix", ""), normalize(var4.getString("category", "OFFENSE")), var4.getDouble("limit", (double)100000.0F));
            }
         }

      }
   }

   private void loadElementTemplates(ConfigurationSection var1) {
      if (var1 != null && !this.elements.isEmpty()) {
         for(String var3 : var1.getKeys(false)) {
            ConfigurationSection var4 = var1.getConfigurationSection(var3);
            if (var4 != null) {
               String var5 = var4.getString("name", "{element}");
               String var6 = var4.getString("suffix", "");
               String var7 = normalize(var4.getString("category", "OFFENSE"));
               double var8 = var4.getDouble("limit", (double)100000.0F);

               for(Element var11 : this.elements.values()) {
                  this.put(var11.id() + normalize(var3), var5.replace("{element}", var11.name()), var6, var7, var8);
               }
            }
         }

      }
   }

   private void loadAliases(ConfigurationSection var1) {
      if (var1 != null) {
         for(String var3 : var1.getKeys(false)) {
            String var4 = normalize(var1.getString(var3, ""));
            if (!var4.isBlank()) {
               this.aliases.put(normalize(var3), var4);
            }
         }

      }
   }

   private void put(String var1, String var2, String var3, String var4, double var5) {
      if (!var1.isBlank()) {
         double var7 = Double.isFinite(var5) && var5 > (double)0.0F ? var5 : (double)100000.0F;
         this.stats.put(var1, new Entry(var1, var2, var3 == null ? "" : var3, var4, var7));
      }
   }

   public boolean isLoaded() {
      return !this.stats.isEmpty();
   }

   public Map<String, Entry> stats() {
      return Collections.unmodifiableMap(new LinkedHashMap(this.stats));
   }

   public Map<String, Category> categories() {
      return Collections.unmodifiableMap(new LinkedHashMap(this.categories));
   }

   public Map<String, String> aliases() {
      return Map.copyOf(this.aliases);
   }

   public Set<String> metadataKeys() {
      return Set.copyOf(this.metadataKeys);
   }

   public Map<String, Element> elements() {
      return Collections.unmodifiableMap(new LinkedHashMap(this.elements));
   }

   public Set<String> loreHidden() {
      return Set.copyOf(this.loreHidden);
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toUpperCase(Locale.ROOT).replace('-', '_');
   }

   public static record Entry(String key, String displayName, String suffix, String category, double limit) {
   }

   public static record Category(String id, String displayName, String color, String icon, int order) {
      public Category(String id, String displayName, String color, String icon, int order) {
         icon = icon == null ? "" : icon.trim();
         this.id = id;
         this.displayName = displayName;
         this.color = color;
         this.icon = icon;
         this.order = order;
      }
   }

   public static record Element(String id, String name, String color) {
   }
}
