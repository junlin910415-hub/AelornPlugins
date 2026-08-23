package tw.linsy.aelorn.mmoitems.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface MMOItemsApi {
   List<MMOItemDefinition> definitions();

   Optional<MMOItemDefinition> definition(String var1, String var2);

   Optional<MMOItemIdentity> inspect(ItemStack var1);

   boolean isManaged(ItemStack var1);

   ItemStack create(String var1, String var2, int var3, int var4, String var5);

   void giveOrDrop(Player var1, ItemStack var2);
}
