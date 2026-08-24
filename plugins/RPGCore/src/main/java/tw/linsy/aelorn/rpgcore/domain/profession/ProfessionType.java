package tw.linsy.aelorn.rpgcore.domain.profession;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ProfessionType {
   FISHING("fishing", "釣魚", "FISHING_ROD", ProfessionCategory.GATHERING),
   WOODCUTTING("woodcutting", "伐木", "IRON_AXE", ProfessionCategory.GATHERING),
   MINING("mining", "採礦", "IRON_PICKAXE", ProfessionCategory.GATHERING),
   FARMING("farming", "耕作", "IRON_HOE", ProfessionCategory.GATHERING),
   SCRIBING("scribing", "抄寫", "WRITABLE_BOOK", ProfessionCategory.CRAFTING),
   JEWELING("jeweling", "珠寶", "AMETHYST_SHARD", ProfessionCategory.CRAFTING),
   ALCHEMY("alchemy", "煉金", "BREWING_STAND", ProfessionCategory.CRAFTING),
   COOKING("cooking", "烹飪", "COOKED_BEEF", ProfessionCategory.CRAFTING),
   WEAPONSMITHING("weaponsmithing", "武器鍛造", "ANVIL", ProfessionCategory.CRAFTING),
   TAILORING("tailoring", "裁縫", "LEATHER", ProfessionCategory.CRAFTING),
   WOODWORKING("woodworking", "木工", "OAK_LOG", ProfessionCategory.CRAFTING),
   ARMOURING("armouring", "護甲鍛造", "IRON_CHESTPLATE", ProfessionCategory.CRAFTING);

   private final String id;
   private final String displayName;
   private final String iconMaterial;
   private final ProfessionCategory category;

   private ProfessionType(String id, String displayName, String iconMaterial, ProfessionCategory category) {
      this.id = id;
      this.displayName = displayName;
      this.iconMaterial = iconMaterial;
      this.category = category;
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public String iconMaterial() {
      return this.iconMaterial;
   }

   public ProfessionCategory category() {
      return this.category;
   }

   public static Optional<ProfessionType> parse(String value) {
      if (value == null) {
         return Optional.empty();
      } else {
         String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
         return Arrays.stream(values()).filter((type) -> type.id.equals(normalized) || type.name().equalsIgnoreCase(normalized)).findFirst();
      }
   }
}
