package tw.linsy.aelorn.rpgcore.domain.profession;

public enum ProfessionCategory {
   GATHERING("採集技能"),
   CRAFTING("製作技能");

   private final String displayName;

   private ProfessionCategory(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }
}
