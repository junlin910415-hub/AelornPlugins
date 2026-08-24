package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.combat.CharacterActivationService;
import tw.linsy.aelorn.rpgcore.combat.CombatEffectsService;
import tw.linsy.aelorn.rpgcore.combat.DamagePipeline;
import tw.linsy.aelorn.rpgcore.combat.HudNotificationService;
import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.combat.DamageResult;
import tw.linsy.aelorn.rpgcore.domain.monster.MonsterDefinition;
import tw.linsy.aelorn.rpgcore.domain.monster.ScaledMonsterStats;
import tw.linsy.aelorn.rpgcore.encounter.EncounterRuntimeService;
import tw.linsy.aelorn.rpgcore.monster.ContributionLedger;
import tw.linsy.aelorn.rpgcore.monster.MonsterLootService;
import tw.linsy.aelorn.rpgcore.monster.MonsterRuntimeService;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import tw.linsy.aelorn.rpgcore.progression.ExperienceCurve;
import tw.linsy.aelorn.rpgcore.progression.ProgressionResult;
import tw.linsy.aelorn.rpgcore.progression.ProgressionService;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MonsterCombatListener implements Listener {
    private static final long CONTRIBUTION_WINDOW_MILLIS = 30_000L;
    private static final double MINIMUM_REWARD_SHARE = 0.05;

    private final MonsterRuntimeService monsters;
    private final ContributionLedger contributions;
    private final MonsterLootService lootService;
    private final CharacterService characterService;
    private final ProgressionService progressionService;
    private final ExperienceCurve experienceCurve;
    private final CharacterActivationService activationService;
    private final DamagePipeline damagePipeline;
    private final HudNotificationService notifications;
    private final MessageBundle messages;
    private final RpgScheduler scheduler;
    private final EncounterRuntimeService encounters;
    private final QuestService quests;
    private final CombatEffectsService effects;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MonsterCombatListener(
            MonsterRuntimeService monsters,
            ContributionLedger contributions,
            MonsterLootService lootService,
            CharacterService characterService,
            ProgressionService progressionService,
            ExperienceCurve experienceCurve,
            CharacterActivationService activationService,
            DamagePipeline damagePipeline,
            HudNotificationService notifications,
            MessageBundle messages,
            RpgScheduler scheduler,
            EncounterRuntimeService encounters,
            QuestService quests) {
        this(monsters, contributions, lootService, characterService, progressionService, experienceCurve,
                activationService, damagePipeline, notifications, messages, scheduler, encounters, quests, null);
    }

    public MonsterCombatListener(
            MonsterRuntimeService monsters,
            ContributionLedger contributions,
            MonsterLootService lootService,
            CharacterService characterService,
            ProgressionService progressionService,
            ExperienceCurve experienceCurve,
            CharacterActivationService activationService,
            DamagePipeline damagePipeline,
            HudNotificationService notifications,
            MessageBundle messages,
            RpgScheduler scheduler,
            EncounterRuntimeService encounters,
            QuestService quests,
            CombatEffectsService effects) {
        this.monsters = monsters;
        this.contributions = contributions;
        this.lootService = lootService;
        this.characterService = characterService;
        this.progressionService = progressionService;
        this.experienceCurve = experienceCurve;
        this.activationService = activationService;
        this.damagePipeline = damagePipeline;
        this.notifications = notifications;
        this.messages = messages;
        this.scheduler = scheduler;
        this.encounters = encounters;
        this.quests = quests;
        this.effects = effects;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMonsterAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile) {
            Double damage = monsters.projectileDamage(projectile).orElse(null);
            ProjectileSource shooter = projectile.getShooter();
            if (damage == null && shooter instanceof LivingEntity source && monsters.isManaged((Entity) source)) {
                damage = monsters.stats((Entity) source).map(ScaledMonsterStats::damage).orElse(null);
            }
            if (damage != null) {
                event.setDamage(damage);
            }
            return;
        }
        if (event.getDamager() instanceof LivingEntity source && monsters.isManaged((Entity) source)) {
            monsters.animateAttack(source);
            double damage = monsters.activeAbilityDamage()
                    .orElseGet(() -> monsters.stats((Entity) source)
                            .map(ScaledMonsterStats::damage)
                            .orElse(event.getDamage()));
            event.setDamage(damage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMonsterDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity monster) || !monsters.isManaged((Entity) monster)) {
            return;
        }
        Player attacker = playerSource(event.getDamager());
        if (attacker == null || characterService.activeCharacter(attacker.getUniqueId()).isEmpty()) {
            return;
        }
        ScaledMonsterStats stats = monsters.stats((Entity) monster).orElse(null);
        if (stats == null) {
            return;
        }
        double rating = (stats.defense() + stats.resistance()) * 0.5;
        double mitigation = Math.min(0.7, rating / (rating + 55.0 + 3.0 * stats.level()));
        double finalDamage = Math.max(0.05, event.getDamage() * (1.0 - mitigation));
        event.setDamage(finalDamage);
        monsters.animateHurt(monster);
        boolean critical = damagePipeline.currentResult()
                .map(DamageResult::critical)
                .orElse(finalDamage >= Math.max(2.0, stats.maximumHealth() * 0.06));
        showHitFeedback(
                attacker,
                monster,
                monsters.definition((Entity) monster).orElse(null),
                finalDamage,
                mitigation,
                critical);
        contributions.record(
                monster.getUniqueId(),
                attacker.getUniqueId(),
                Math.min(monster.getHealth(), finalDamage),
                System.currentTimeMillis());
        encounters.recordContribution(monster.getUniqueId(), attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMonsterDeath(EntityDeathEvent event) {
        LivingEntity monster = event.getEntity();
        if (!monsters.isManaged((Entity) monster)) {
            return;
        }
        MonsterDefinition definition = monsters.definition((Entity) monster).orElse(null);
        ScaledMonsterStats stats = monsters.stats((Entity) monster).orElse(null);
        monsters.animateDeath(monster);
        monsters.stop((Entity) monster);
        encounters.monsterDefeated(monster.getUniqueId());
        if (definition == null || stats == null) {
            contributions.clear(monster.getUniqueId());
            return;
        }

        event.getDrops().clear();
        event.getDrops().addAll(lootService.roll(definition, stats.level()));
        event.setDroppedExp(0);
        Map<UUID, Double> shares = contributions.settle(
                monster.getUniqueId(), System.currentTimeMillis(), CONTRIBUTION_WINDOW_MILLIS);
        Player killer = monster.getKiller();
        UUID killerId = killer == null ? null : killer.getUniqueId();
        UUID topContributor = shares.keySet().stream().findFirst().orElse(killerId);
        for (Map.Entry<UUID, Double> entry : shares.entrySet()) {
            if (entry.getValue() < MINIMUM_REWARD_SHARE
                    && !entry.getKey().equals(killerId)
                    && !entry.getKey().equals(topContributor)) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            CharacterProfile character = player == null
                    ? null
                    : characterService.activeCharacter(entry.getKey()).orElse(null);
            if (player == null || !player.isOnline() || character == null) {
                continue;
            }
            double contributionMultiplier = Math.max(0.2, Math.min(1.0, entry.getValue() * 1.25));
            long reward = Math.max(1L, Math.round(
                    stats.experience()
                            * contributionMultiplier
                            * experienceCurve.rewardMultiplier(character.level(), stats.level())));
            scheduler.executeEntity((Entity) player, () -> reward(player, definition, reward), () -> { });
        }
    }

    private void reward(Player player, MonsterDefinition definition, long experience) {
        if (!player.isOnline() || characterService.activeCharacter(player.getUniqueId()).isEmpty()) {
            return;
        }
        ProgressionResult result = progressionService.grantExperience(player.getUniqueId(), experience);
        String monsterName = miniMessage.stripTags(definition.displayName());
        notifications.show(player.getUniqueId(), messages.content(
                "monster-xp",
                MessageBundle.value("experience", Long.toString(result.awardedExperience())),
                MessageBundle.value("monster", monsterName)));
        if (result.leveledUp()) {
            player.sendMessage(messages.message(
                    "level-up", MessageBundle.value("level", Integer.toString(result.level()))));
            characterService.activeCharacter(player.getUniqueId())
                    .ifPresent(character -> activationService.activate(player, character));
        }
        quests.recordMonsterKill(player, definition.id());
    }

    private Player playerSource(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void showHitFeedback(
            Player attacker,
            LivingEntity monster,
            MonsterDefinition definition,
            double finalDamage,
            double mitigation,
            boolean critical) {
        if (effects != null) {
            effects.showImpact(attacker, monster, definition, finalDamage, critical);
        }
        String prefix = critical ? "<gold>暴擊</gold> " : "";
        String reduction = mitigation > 0.0
                ? " <dark_gray>減傷 " + Math.round(mitigation * 100.0) + "%</dark_gray>"
                : "";
        notifications.show(
                attacker.getUniqueId(),
                miniMessage.deserialize(
                        prefix + "<red>" + formatDamage(finalDamage) + "</red><gray> 傷害</gray>" + reduction));
    }

    private String formatDamage(double damage) {
        if (damage >= 100.0) {
            return Long.toString(Math.round(damage));
        }
        return String.format(Locale.US, "%.1f", damage);
    }
}
