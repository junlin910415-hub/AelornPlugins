package com.xuzhihuanjing.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.InventoryHolder;

public interface CharacterSelectorHolder extends InventoryHolder {
   UUID ownerId();
}
