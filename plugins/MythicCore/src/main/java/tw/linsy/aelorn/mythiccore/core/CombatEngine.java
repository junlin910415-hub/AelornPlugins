package tw.linsy.aelorn.mythiccore.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import tw.linsy.aelorn.mythiccore.api.ElementProfile;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;
import tw.linsy.aelorn.mythiccore.combat.CombatResult;

public final class CombatEngine {
   private final StatSnapshotService snapshots;
   private final ItemDataService itemData;
   private final PlayerClassStateService classStates;
   private final Supplier<Map<String, ElementProfile>> elements;
   private final ConcurrentHashMap<UUID, Long> abilityCooldowns = new ConcurrentHashMap();
   private final ThreadLocal<Boolean> internalDamage = ThreadLocal.withInitial(() -> false);
   private volatile Settings settings = CombatEngine.Settings.from(new YamlConfiguration());

   public CombatEngine(StatSnapshotService var1, ItemDataService var2, PlayerClassStateService var3, Supplier<Map<String, ElementProfile>> var4) {
      this.snapshots = var1;
      this.itemData = var2;
      this.classStates = var3;
      this.elements = var4;
   }

   public void reload(FileConfiguration var1) {
      this.settings = CombatEngine.Settings.from(var1);
   }

   public Settings settings() {
      return this.settings;
   }

   public void clearCooldowns() {
      this.abilityCooldowns.clear();
   }

   public void removeCooldown(UUID var1) {
      this.abilityCooldowns.remove(var1);
   }

   public void handleDamage(EntityDamageByEntityEvent var1) {
      Settings var2 = this.settings;
      if (var2.enabled() && !(Boolean)this.internalDamage.get()) {
         LivingEntity var3 = unwrapAttacker(var1.getDamager());
         if (var3 != null) {
            Entity var5 = var1.getEntity();
            if (var5 instanceof LivingEntity) {
               LivingEntity var4 = (LivingEntity)var5;
               StatSnapshot var10 = this.snapshots.snapshot(var3);
               StatSnapshot var6 = this.snapshots.snapshot(var4);
               ItemDataService.ItemCombatData var7 = this.itemData.readCombatData(mainHand(var3));
               CombatResult var8 = this.calculate(var2, var3, var10, var6, var7, var1.getDamage());
               var1.setDamage(var8.finalDamage());
               if (var8.finalDamage() > (double)0.0F) {
                  this.applyLifeSteal(var2, var3, var10, var8.finalDamage());
                  this.triggerOnHit(var2, var3, var4, var7, var8);
               }

               if (var2.actionbarIndicators() && var3 instanceof Player) {
                  Player var9 = (Player)var3;
                  var9.sendActionBar(Component.text(var8.indicator(), var8.critical() ? NamedTextColor.GOLD : NamedTextColor.GRAY));
               }

               return;
            }
         }

      }
   }

   public double calculateAttackDamage(LivingEntity var1, LivingEntity var2, double var3) {
      Settings var5 = this.settings;
      StatSnapshot var6 = this.snapshots.snapshot(var1);
      StatSnapshot var7 = this.snapshots.snapshot(var2);
      ItemDataService.ItemCombatData var8 = this.itemData.readCombatData(mainHand(var1));
      return this.calculate(var5, var1, var6, var7, var8, var3).finalDamage();
   }

   private CombatResult calculate(Settings var1, LivingEntity var2, StatSnapshot var3, StatSnapshot var4, ItemDataService.ItemCombatData var5, double var6) {
      double var8 = this.sanitizeDamage(var1, var6);
      if (var2 instanceof Player var10) {
         String var11 = this.classStates.itemUseFailure(var10, var5);
         if (!var11.isBlank()) {
            return new CombatResult(var8, (double)0.0F, false, (double)1.0F, var11);
         }
      }

      double var51 = (double)1.0F + percent(var3.get("ALL_DAMAGE") + var3.get("PVE_DAMAGE") + var3.get("PVP_DAMAGE")) + var3.get("DEXTERITY") * 0.002 + var3.get("WISDOM") * 0.0014 + var3.get("INTELLIGENCE") * 9.0E-4;
      double var12 = (double)1.0F + var3.get("STRENGTH") * 0.0019 + var3.get("DEXTERITY") * 0.001;
      double var14 = (double)1.0F + var3.get("INTELLIGENCE") * 0.0019 + var3.get("WISDOM") * 0.0013;
      double var16 = (double)1.0F + var3.get("FIRE_DAMAGE") * 6.0E-4 + var3.get("ICE_DAMAGE") * 6.0E-4 + var3.get("THUNDER_DAMAGE") * 7.0E-4 + var3.get("WIND_DAMAGE") * 6.0E-4 + var3.get("EARTH_DAMAGE") * 6.0E-4 + var3.get("WATER_DAMAGE") * 6.0E-4 + var3.get("DARKNESS_DAMAGE") * 7.0E-4 + var3.get("LIGHTNESS_DAMAGE") * 7.0E-4 + var3.get("ARCANE_DAMAGE") * 6.0E-4;
      double var18 = clamp(var51, 0.35, var1.comboScalarCap());
      double var20 = clamp(var12 * var18, 0.55, 2.6);
      double var22 = clamp(var14 * var18, 0.55, 2.6);
      double var24 = clamp(var16 * var18, 0.55, 2.6);
      double var26 = Math.max((double)0.0F, var8 * var1.vanillaDamageWeight() + var3.get("ATTACK_DAMAGE") + var3.get("WEAPON_DAMAGE") + softCap(var3.get("PHYSICAL_DAMAGE"), (double)140.0F) + var3.get("PROJECTILE_DAMAGE") * 0.35) * var1.weaponDamageBonus() * var20;
      double var28 = Math.max((double)0.0F, var3.get("MAGIC_DAMAGE") * 0.72 + var3.get("SKILL_DAMAGE") + var3.get("ABILITY_DAMAGE") * 0.55) * var1.magicDamageBonus() * var22;
      double var30 = this.elementalDamage(var3) * var1.elementDamageBonus() * var24;
      double var32 = boundedPercent(var3.get("CRITICAL_STRIKE_CHANCE"), (double)90.0F) + curve(var3.get("CRITICAL_STRIKE_RATING"), (double)720.0F, (double)30.0F);
      boolean var34 = ThreadLocalRandom.current().nextDouble((double)100.0F) < Math.min((double)95.0F, Math.max((double)0.0F, var32));
      if (var34) {
         double var35 = var3.get("CRITICAL_STRIKE_POWER") <= (double)0.0F ? var1.defaultCritPower() : var3.get("CRITICAL_STRIKE_POWER");
         double var37 = curve(var4.get("RESILIENCE"), (double)900.0F, 0.55);
         double var39 = (double)1.0F + Math.max((double)0.0F, (var35 - (double)100.0F) / (double)100.0F) * ((double)1.0F - var37);
         var26 *= var39;
         var28 *= var39;
         var30 *= var39;
      }

      double var52 = this.mitigate(var1, var26, var4.get("DEFENSE"), var3.get("ARMOR_PENETRATION"), var3.get("ARMOR_PENETRATION_PERCENT"), var1.defenseK(), var1.defenseCap(), var1.defenseDamageWeight());
      double var53 = this.mitigate(var1, var28, var4.get("MAGIC_RESISTANCE"), var3.get("MAGIC_PENETRATION"), var3.get("MAGIC_PENETRATION_PERCENT"), var1.resistanceK(), var1.magicResistanceCap(), var1.magicDamageWeight());
      double var54 = this.mitigate(var1, var30, var4.get("ELEMENTAL_RESISTANCE"), var3.get("ELEMENTAL_PENETRATION"), var3.get("ELEMENTAL_PENETRATION_PERCENT"), var1.resistanceK(), var1.elementResistanceCap(), var1.elementDamageWeight());
      MitigationRoll var41 = this.rollAvoidance(var1, var4);
      double var42 = boundedPercent(var4.get("DAMAGE_REDUCTION"), (double)80.0F) / (double)100.0F;
      double var44 = (var52 + var53 + var54) * ((double)1.0F - var42);
      double var46 = Math.min(var1.maxFinalDamage(), Math.max(var1.minimumDamage(), var44 * var41.damageMultiplier()));
      double var48 = var44 <= (double)0.0F ? (double)0.0F : (double)1.0F - var46 / Math.max(1.0E-4, var26 + var28 + var30);
      String var50 = var41.label().isBlank() ? (var34 ? "暴擊" : "傷害") : var41.label();
      return new CombatResult(this.sanitizeDamage(var1, var26 + var28 + var30), var46, var34, Math.max((double)0.0F, var48), var50);
   }

   private MitigationRoll rollAvoidance(Settings var1, StatSnapshot var2) {
      double var3 = boundedPercent(var2.get("DODGE_RATING"), var1.dodgeCap());
      if (ThreadLocalRandom.current().nextDouble((double)100.0F) < var3) {
         return new MitigationRoll(0.05, "閃避");
      } else {
         double var5 = boundedPercent(var2.get("PARRY_RATING"), var1.parryCap());
         if (ThreadLocalRandom.current().nextDouble((double)100.0F) < var5) {
            return new MitigationRoll(0.55, "招架");
         } else {
            double var7 = boundedPercent(var2.get("BLOCK_RATING"), var1.blockCap());
            if (ThreadLocalRandom.current().nextDouble((double)100.0F) < var7) {
               double var9 = boundedPercent(var2.get("BLOCK_POWER"), (double)80.0F);
               return new MitigationRoll((double)1.0F - var9 / (double)100.0F, "格擋");
            } else {
               return new MitigationRoll((double)1.0F, "");
            }
         }
      }
   }

   private double mitigate(Settings var1, double var2, double var4, double var6, double var8, double var10, double var12, double var14) {
      double var16 = this.sanitizeDamage(var1, var2);
      if (var16 <= (double)0.0F) {
         return (double)0.0F;
      } else {
         double var18 = Math.max((double)0.0F, (var4 - Math.max((double)0.0F, var6)) * ((double)1.0F - clamp01(var8 / (double)100.0F)));
         double var20 = Math.max((double)0.0F, var14);
         double var22 = Math.min(var12, var18 / (var18 + var10 + var16 * var20));
         return Math.min(var1.maxFinalDamage(), var16 * ((double)1.0F - var22));
      }
   }

   private void triggerOnHit(Settings var1, LivingEntity var2, LivingEntity var3, ItemDataService.ItemCombatData var4, CombatResult var5) {
      String var6 = var4.onHitAbility();
      if (!var6.isBlank() && this.claimAbilityCooldown(var1, var2.getUniqueId())) {
         double var7 = boundedPercent(var4.abilityChance((double)100.0F), (double)100.0F);
         if (!(ThreadLocalRandom.current().nextDouble((double)100.0F) > var7)) {
            double var9 = Math.min(var1.maxInternalAbilityDamage(), Math.max((double)0.0F, var4.abilityPower(Math.max((double)1.0F, var5.finalDamage() * 0.16))));
            switch (var6) {
               case "BURN":
               case "灼燒":
                  var3.setFireTicks(Math.max(var3.getFireTicks(), (int)Math.round((double)60.0F + var9 * (double)8.0F)));
                  break;
               case "FROST":
               case "冰霜":
                  var3.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, true, true));
                  break;
               case "SHOCK":
               case "雷擊":
                  var3.getWorld().strikeLightningEffect(var3.getLocation());
                  this.dealInternalDamage(var1, var3, var2, var9);
                  break;
               case "BLEED":
               case "流血":
                  this.dealInternalDamage(var1, var3, var2, var9 * (double)0.75F);
                  break;
               case "HEAL":
               case "治癒":
                  this.heal(var1, var2, var9);
            }

         }
      }
   }

   private boolean claimAbilityCooldown(Settings var1, UUID var2) {
      long var3 = var1.abilityCooldownMillis();
      if (var3 <= 0L) {
         return true;
      } else {
         long var5 = System.currentTimeMillis();
         Long var7 = (Long)this.abilityCooldowns.get(var2);
         if (var7 != null && var5 - var7 < var3) {
            return false;
         } else {
            this.abilityCooldowns.put(var2, var5);
            if (this.abilityCooldowns.size() > 5000) {
               this.abilityCooldowns.entrySet().removeIf((var4) -> var5 - (Long)var4.getValue() > var3 * 20L);
            }

            return true;
         }
      }
   }

   private void dealInternalDamage(Settings var1, LivingEntity var2, LivingEntity var3, double var4) {
      double var6 = Math.min(var1.maxInternalAbilityDamage(), this.sanitizeDamage(var1, var4));
      if (!(var6 <= (double)0.0F)) {
         this.internalDamage.set(true);

         try {
            var2.damage(var6, var3);
         } finally {
            this.internalDamage.set(false);
         }

      }
   }

   private void applyLifeSteal(Settings var1, LivingEntity var2, StatSnapshot var3, double var4) {
      double var6 = var3.get("LIFE_STEAL");
      if (var6 > (double)0.0F && var4 > (double)0.0F) {
         this.heal(var1, var2, var4 * Math.min((double)100.0F, var6) / (double)100.0F);
      }

   }

   private void heal(Settings var1, LivingEntity var2, double var3) {
      double var5 = Math.min(var1.maxHeal(), Math.max((double)0.0F, var3));
      AttributeInstance var7 = var2.getAttribute(Attribute.MAX_HEALTH);
      double var8 = var7 == null ? var2.getHealth() : var7.getValue();
      var2.setHealth(Math.min(var8, var2.getHealth() + var5));
   }

   public double elementalDamage(StatSnapshot var1) {
      Map var2 = (Map)this.elements.get();
      if (var2.isEmpty()) {
         return var1.get("FIRE_DAMAGE") + var1.get("ICE_DAMAGE") + var1.get("THUNDER_DAMAGE") + var1.get("WIND_DAMAGE") + var1.get("EARTH_DAMAGE") + var1.get("WATER_DAMAGE") + var1.get("DARKNESS_DAMAGE") + var1.get("LIGHTNESS_DAMAGE") + var1.get("ARCANE_DAMAGE") + var1.get("NATURE_DAMAGE");
      } else {
         double var3 = (double)0.0F;

         for(ElementProfile var6 : var2.values()) {
            var3 += balancedElementDamage(var1, var6);
         }

         return var3;
      }
   }

   public double balancedElementDamage(StatSnapshot var1, String var2) {
      if (var1 == null) {
         return (double)0.0F;
      } else {
         ElementProfile var3 = (ElementProfile)((Map)this.elements.get()).get(StatSnapshot.normalize(var2));
         return var3 == null ? (double)0.0F : balancedElementDamage(var1, var3);
      }
   }

   private static double balancedElementDamage(StatSnapshot var0, ElementProfile var1) {
      double var2 = (double)0.0F;

      for(String var5 : var1.damageStats()) {
         var2 += var0.get(var5);
      }

      return softCap(Math.max((double)0.0F, var2) * var1.damageMultiplier(), var1.softCap());
   }

   private static LivingEntity unwrapAttacker(Entity var0) {
      if (var0 instanceof LivingEntity var4) {
         return var4;
      } else {
         if (var0 instanceof Projectile var1) {
            ProjectileSource var2 = var1.getShooter();
            if (var2 instanceof LivingEntity var3) {
               return var3;
            }
         }

         return null;
      }
   }

   private static ItemStack mainHand(LivingEntity var0) {
      EntityEquipment var1 = var0.getEquipment();
      return var1 == null ? null : var1.getItemInMainHand();
   }

   private double sanitizeDamage(Settings var1, double var2) {
      return !Double.isFinite(var2) ? (double)0.0F : Math.max((double)0.0F, Math.min(var1.maxFinalDamage(), var2));
   }

   private static double softCap(double var0, double var2) {
      double var4 = Math.max((double)1.0F, var2);
      double var6 = Math.max((double)0.0F, var0);
      return var4 * var6 / (var6 + var4);
   }

   private static double curve(double var0, double var2, double var4) {
      double var6 = Math.max((double)0.0F, var0);
      return var6 <= (double)0.0F ? (double)0.0F : var4 * var6 / (var6 + var2);
   }

   private static double boundedPercent(double var0, double var2) {
      return Math.max((double)0.0F, Math.min(var2, var0));
   }

   private static double percent(double var0) {
      return var0 / (double)100.0F;
   }

   private static double clamp(double var0, double var2, double var4) {
      return Math.max(var2, Math.min(var4, var0));
   }

   private static double clamp01(double var0) {
      return Math.max((double)0.0F, Math.min((double)1.0F, var0));
   }

   public static record Settings(boolean enabled, boolean actionbarIndicators, double defenseK, double resistanceK, double defenseDamageWeight, double magicDamageWeight, double elementDamageWeight, double defenseCap, double resistanceCap, double magicResistanceCap, double elementResistanceCap, double dodgeCap, double parryCap, double blockCap, double defaultCritPower, double weaponDamageBonus, double magicDamageBonus, double elementDamageBonus, double comboScalarCap, double minimumDamage, double vanillaDamageWeight, double maxFinalDamage, double maxInternalAbilityDamage, double maxHeal, double skillMaxTotalDamage, long abilityCooldownMillis) {
      public static Settings from(FileConfiguration var0) {
         double var1 = Math.max((double)0.0F, var0.getDouble("combat.minimum-damage", (double)0.5F));
         double var3 = Math.max((double)1.0F, var0.getDouble("safety.max-internal-ability-damage", (double)50000.0F));
         return new Settings(var0.getBoolean("combat.enabled", true), var0.getBoolean("combat.actionbar-indicators", true), Math.max((double)1.0F, var0.getDouble("combat.formula.defense-k", (double)95.0F)), Math.max((double)1.0F, var0.getDouble("combat.formula.resistance-k", (double)85.0F)), Math.max((double)0.0F, var0.getDouble("combat.formula.damage-weight", 2.2)), Math.max((double)0.0F, var0.getDouble("combat.formula.magic-defense-weight", 2.2)), Math.max((double)0.0F, var0.getDouble("combat.formula.element-defense-weight", 2.2)), CombatEngine.clamp01(var0.getDouble("combat.formula.defense-cap", 0.82)), CombatEngine.clamp01(var0.getDouble("combat.formula.resistance-cap", 0.76)), CombatEngine.clamp01(var0.getDouble("combat.formula.magic-resistance-cap", 0.8)), CombatEngine.clamp01(var0.getDouble("combat.formula.element-resistance-cap", 0.8)), Math.max((double)0.0F, var0.getDouble("combat.formula.dodge-cap", (double)35.0F)), Math.max((double)0.0F, var0.getDouble("combat.formula.parry-cap", (double)45.0F)), Math.max((double)0.0F, var0.getDouble("combat.formula.block-cap", (double)55.0F)), Math.max((double)1.0F, var0.getDouble("combat.formula.default-critical-power", (double)175.0F)), Math.max(0.2, var0.getDouble("combat.formula.weapon-bonus", (double)1.0F)), Math.max(0.2, var0.getDouble("combat.formula.magic-bonus", (double)1.0F)), Math.max(0.2, var0.getDouble("combat.formula.element-bonus", (double)1.0F)), Math.max((double)1.0F, Math.min((double)3.0F, var0.getDouble("combat.formula.combo-scalar-cap", 2.4))), var1, Math.max((double)0.0F, var0.getDouble("combat.formula.vanilla-damage-weight", 0.35)), Math.max(var1, var0.getDouble("safety.max-final-damage", (double)250000.0F)), var3, Math.max((double)1.0F, var0.getDouble("safety.max-heal", (double)50000.0F)), Math.max((double)1.0F, Math.min(var3, var0.getDouble("skill-balance.max-total-damage", var3))), Math.max(0L, var0.getLong("combat.ability-cooldown-ms", 450L)));
      }
   }

   private static record MitigationRoll(double damageMultiplier, String label) {
   }
}
