package tw.linsy.aelorn.rpgcore.integration.nexo;

import com.nexomc.nexo.api.NexoItems;
import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

final class NexoCustomItemProvider implements CustomItemProvider {
   private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
      Map.entry("rpgcore_wayfinder_codex", "aeloria_wayfinder_codex"),
      Map.entry("rpgcore_skill_crystal", "aeloria_skill_crystal"),
      Map.entry("rpgcore_ability_tree", "aeloria_ability_rune"),
      Map.entry("rpgcore_profession_tome", "aeloria_profession_tome"),
      Map.entry("rpgcore_character_profile", "aeloria_character_profile"),
      Map.entry("rpgcore_unidentified_weapon", "aeloria_unidentified_weapon"),
      Map.entry("rpgcore_unidentified_armor", "aeloria_unidentified_armor"),
      Map.entry("rpgcore_unidentified_accessory", "aeloria_unidentified_accessory"),
      Map.entry("gold_coin", "aeloria_gold_coin")
   );

   @Override
   public Optional<ItemStack> build(String itemId) {
      if (itemId == null || itemId.isBlank()) {
         return Optional.empty();
      }
      String nexoId = LEGACY_ALIASES.getOrDefault(itemId, itemId);
      return NexoItems.optionalItemFromId(nexoId).map(builder -> builder.build());
   }
}

