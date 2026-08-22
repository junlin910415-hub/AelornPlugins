package com.xuzhihuanjing.rpgcore.integration.nexo;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface CustomItemProvider {
   Optional<ItemStack> build(String itemId);

   static CustomItemProvider empty() {
      return itemId -> Optional.empty();
   }
}
