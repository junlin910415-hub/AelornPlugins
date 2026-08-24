package tw.linsy.aelorn.rpgcore.combat;

import tw.linsy.aelorn.rpgcore.config.ClassRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.classes.CharacterClassDefinition;
import tw.linsy.aelorn.rpgcore.domain.combat.CombatStats;
import tw.linsy.aelorn.rpgcore.domain.stats.BaseStats;
import tw.linsy.aelorn.rpgcore.domain.stats.PrimarySkill;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentBonuses;
import tw.linsy.aelorn.rpgcore.equipment.EquipmentStatType;
import tw.linsy.aelorn.rpgcore.progression.PrimarySkillService;

public final class StatService {
   private final ClassRegistry classRegistry;
   private final PrimarySkillService primarySkills;

   public StatService(ClassRegistry classRegistry, PrimarySkillService primarySkills) {
      this.classRegistry = classRegistry;
      this.primarySkills = primarySkills;
   }

   public CombatStats calculate(CharacterProfile character) {
      return this.calculate(character, EquipmentBonuses.empty());
   }

   public CombatStats calculate(CharacterProfile character, EquipmentBonuses bonuses) {
      CharacterClassDefinition definition = (CharacterClassDefinition)this.classRegistry.find(character.classId()).orElseThrow(() -> new IllegalArgumentException("Unknown character class: " + character.classId()));
      BaseStats base = definition.baseStats();
      int levelOffset = Math.max(0, character.level() - 1);
      int strength = this.primarySkillPoints(character, bonuses, PrimarySkill.STRENGTH);
      int dexterity = this.primarySkillPoints(character, bonuses, PrimarySkill.DEXTERITY);
      int intelligence = this.primarySkillPoints(character, bonuses, PrimarySkill.INTELLIGENCE);
      int defence = this.primarySkillPoints(character, bonuses, PrimarySkill.DEFENCE);
      int agility = this.primarySkillPoints(character, bonuses, PrimarySkill.AGILITY);
      double intelligenceBonus = this.primarySkills.effectPercent(PrimarySkill.INTELLIGENCE, intelligence) / (double)100.0F;
      double agilityBonus = this.primarySkills.effectPercent(PrimarySkill.AGILITY, agility) / (double)100.0F;
      double healthMultiplier = this.percentMultiplier(bonuses.value(EquipmentStatType.HEALTH_PERCENT));
      double attackMultiplier = this.percentMultiplier(bonuses.value(EquipmentStatType.MAIN_ATTACK_DAMAGE_PERCENT) + bonuses.value(EquipmentStatType.ALL_DAMAGE) + bonuses.value(EquipmentStatType.PHYSICAL_DAMAGE));
      double equipmentReduction = Math.min(0.85, Math.max((double)0.0F, (double)bonuses.value(EquipmentStatType.DAMAGE_REDUCTION) / (double)100.0F));
      double criticalChance = Math.min(0.95, this.primarySkills.criticalChance(dexterity) + Math.max((double)0.0F, (double)bonuses.value(EquipmentStatType.CRITICAL_CHANCE) / (double)100.0F));
      return new CombatStats(Math.max((double)1.0F, (base.health() * ((double)1.0F + (double)levelOffset * 0.045) + (double)bonuses.value(EquipmentStatType.HEALTH)) * healthMultiplier), Math.max((double)1.0F, (base.mana() + (double)levelOffset * (double)1.25F + (double)bonuses.value(EquipmentStatType.MANA)) * ((double)1.0F + intelligenceBonus)), Math.max((double)0.0F, (base.attack() * ((double)1.0F + (double)levelOffset * 0.035) + (double)bonuses.value(EquipmentStatType.ATTACK) + (double)bonuses.value(EquipmentStatType.MAIN_ATTACK_DAMAGE) + (double)bonuses.value(EquipmentStatType.MAGIC_DAMAGE)) * this.primarySkills.damageMultiplier(strength) * attackMultiplier), Math.max((double)0.0F, base.defense() * ((double)1.0F + (double)levelOffset * 0.03) + (double)bonuses.value(EquipmentStatType.DEFENSE) + (double)bonuses.value(EquipmentStatType.ARMOR)), Math.max((double)0.0F, base.resistance() * ((double)1.0F + (double)levelOffset * 0.03) + (double)bonuses.value(EquipmentStatType.RESISTANCE) + (double)bonuses.value(EquipmentStatType.ELEMENTAL_RESISTANCE)), Math.max((double)60.0F, base.speed() + Math.min((double)10.0F, (double)levelOffset * 0.1) + (double)bonuses.value(EquipmentStatType.SPEED) + agilityBonus * (double)12.0F), definition.balance().damageTakenMultiplier() * this.primarySkills.damageTakenMultiplier(defence) * ((double)1.0F - equipmentReduction), definition.balance().basicAttackMultiplier(), strength, dexterity, intelligence, defence, agility, criticalChance, this.primarySkills.spellCostReduction(intelligence), this.primarySkills.dodgeChance(agility), (double)bonuses.value(EquipmentStatType.KNOCKBACK) / (double)100.0F, Math.max((double)0.0F, (double)bonuses.value(EquipmentStatType.HEALTH_REGEN)), Math.max((double)0.0F, (double)bonuses.value(EquipmentStatType.MANA_REGEN)));
   }

   private double percentMultiplier(int percent) {
      return Math.max(0.1, Math.min((double)11.0F, (double)1.0F + (double)percent / (double)100.0F));
   }

   private int primarySkillPoints(CharacterProfile character, EquipmentBonuses bonuses, PrimarySkill skill) {
      int invested = (Integer)character.skillPoints().getOrDefault(skill, 0);
      return Math.max(0, invested + bonuses.primarySkillBonus(skill));
   }
}
