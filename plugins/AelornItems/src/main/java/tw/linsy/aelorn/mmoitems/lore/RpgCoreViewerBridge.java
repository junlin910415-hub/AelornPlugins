package tw.linsy.aelorn.mmoitems.lore;

import com.xuzhihuanjing.rpgcore.equipment.EquipmentRequirementReport;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class RpgCoreViewerBridge {
   private final Server server;
   private final Logger logger;
   private boolean warned;

   public RpgCoreViewerBridge(Server var1, Logger var2) {
      this.server = var1;
      this.logger = var2;
   }

   public List<EquipmentRequirementReport.Entry> requirements(Player var1, ItemStack var2) {
      if (var1 != null && var2 != null) {
         try {
            EquipmentService var3 = (EquipmentService)this.service(EquipmentService.class);
            CharacterService var4 = (CharacterService)this.service(CharacterService.class);
            return var3 != null && var4 != null ? (List)var4.activeCharacter(var1.getUniqueId()).map((var2x) -> var3.inspectRequirements(var2, var2x).entries()).orElseGet(List::of) : List.of();
         } catch (RuntimeException | LinkageError var5) {
            if (!this.warned) {
               this.warned = true;
               this.logger.log(Level.WARNING, "無法向 RPGCore 查詢裝備需求，提示框將不顯示 ✔ / ✖ 標記", var5);
            }

            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private <T> T service(Class<T> var1) {
      RegisteredServiceProvider var2 = this.server.getServicesManager().getRegistration(var1);
      return (T)(var2 == null ? null : var2.getProvider());
   }
}
