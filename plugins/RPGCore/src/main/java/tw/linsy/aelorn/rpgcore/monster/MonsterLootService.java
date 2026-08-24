package tw.linsy.aelorn.rpgcore.monster;

import tw.linsy.aelorn.rpgcore.domain.monster.MonsterDefinition;
import tw.linsy.aelorn.rpgcore.domain.monster.MonsterDropDefinition;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentService;
import tw.linsy.aelorn.rpgcore.equipment.MonsterEquipmentDropDefinition;
import tw.linsy.aelorn.rpgcore.item.CustomItemMarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MonsterLootService {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final EquipmentService equipmentService;
    private final CustomItemMarker customItems;

    public MonsterLootService(EquipmentService equipmentService) {
        this(equipmentService, null);
    }

    public MonsterLootService(EquipmentService equipmentService, CustomItemMarker customItems) {
        this.equipmentService = equipmentService;
        this.customItems = customItems;
    }

    public List<ItemStack> roll(MonsterDefinition definition, int level) {
        ArrayList<ItemStack> items = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int dropIndex = 0;
        for (MonsterDropDefinition drop : definition.drops()) {
            if (random.nextDouble() <= drop.chance()) {
                int amount = random.nextInt(drop.minimumAmount(), drop.maximumAmount() + 1);
                ItemStack item = new ItemStack(Material.valueOf(drop.material()), amount);
                item.editMeta(meta -> {
                    meta.customName(miniMessage.deserialize(drop.displayName()));
                    meta.lore(List.of(
                            miniMessage.deserialize("<dark_gray>艾洛恩怪物素材</dark_gray>"),
                            miniMessage.deserialize("<gray>來源：</gray>" + definition.displayName()),
                            miniMessage.deserialize("<gray>等級：</gray><white>" + level + "</white>")));
                });
                if (customItems != null) {
                    String contentId = definition.id() + "." + drop.material().toLowerCase(Locale.ROOT) + "." + dropIndex;
                    customItems.mark(item, "monster_drop", contentId);
                }
                items.add(item);
            }
            dropIndex++;
        }
        for (MonsterEquipmentDropDefinition drop : definition.equipmentDrops()) {
            if (random.nextDouble() <= drop.chance()) {
                equipmentService.createUnidentified(drop, level).ifPresent(items::add);
            }
        }
        return items;
    }
}
