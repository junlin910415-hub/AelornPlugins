package com.xuzhihuanjing.rpgcore.monster;

import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDropDefinition;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.equipment.MonsterEquipmentDropDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MonsterLootService {
   private final MiniMessage miniMessage = MiniMessage.miniMessage();
   private final EquipmentService equipmentService;

   public MonsterLootService(EquipmentService equipmentService) {
      this.equipmentService = equipmentService;
   }

   public List<ItemStack> roll(MonsterDefinition definition, int level) {
      List<ItemStack> items = new ArrayList();
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for(MonsterDropDefinition drop : definition.drops()) {
         if (!(random.nextDouble() > drop.chance())) {
            int amount = random.nextInt(drop.minimumAmount(), drop.maximumAmount() + 1);
            ItemStack item = new ItemStack(Material.valueOf(drop.material()), amount);
            item.editMeta((meta) -> meta.customName(this.miniMessage.deserialize(drop.displayName())));
            items.add(item);
         }
      }

      for(MonsterEquipmentDropDefinition drop : definition.equipmentDrops()) {
         if (!(random.nextDouble() > drop.chance())) {
            var var10000 = this.equipmentService.createUnidentified(drop, level);
            Objects.requireNonNull(items);
            var10000.ifPresent(items::add);
         }
      }

      return items;
   }
}
