package tw.linsy.aelorn.rpgcore.equipment;

public enum EquipmentRarity {
   COMMON("common", "<white>凡品</white>", (double)1.0F),
   UNCOMMON("uncommon", "<green>優良</green>", 1.08),
   RARE("rare", "<aqua>稀有</aqua>", 1.26),
   EPIC("epic", "<light_purple>史詩</light_purple>", 1.44),
   LEGENDARY("legendary", "<gold>傳說</gold>", 1.56),
   VAST("vast", "<aqua>浩瀚</aqua>", 1.68),
   MYTHIC("mythic", "<red>神話</red>", 1.82);

   private final String id;
   private final String displayName;
   private final double statMultiplier;

   private EquipmentRarity(String id, String displayName, double statMultiplier) {
      this.id = id;
      this.displayName = displayName;
      this.statMultiplier = statMultiplier;
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public double statMultiplier() {
      return this.statMultiplier;
   }
}
