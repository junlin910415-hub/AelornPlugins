package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.HudSettings;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestProgress;
import com.xuzhihuanjing.rpgcore.platform.RpgScheduler;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class NavigationHudService {
   private static final String OBJECTIVE_NAME = "rpg_navigation";
   private final QuestRegistry questRegistry;
   private final MessageBundle messages;
   private final HudSettings settings;
   private final boolean scoreboardSupported;
   private final Map<UUID, Session> sessions = new ConcurrentHashMap();

   public NavigationHudService(QuestRegistry questRegistry, MessageBundle messages, HudSettings settings, RpgScheduler scheduler) {
      this.questRegistry = questRegistry;
      this.messages = messages;
      this.settings = settings;
      this.scoreboardSupported = !scheduler.isFolia();
   }

   public void start(Player player, CharacterProfile character) {
      if (this.settings.sidebarEnabled() && this.scoreboardSupported) {
         this.stop(player);
         ScoreboardManager manager = Bukkit.getScoreboardManager();
         if (manager != null) {
            Scoreboard previous = player.getScoreboard();
            Scoreboard scoreboard = manager.getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("rpg_navigation", Criteria.DUMMY, this.messages.text(this.settings.sidebarTitle()));
            objective.numberFormat(NumberFormat.blank());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            Session session = new Session(previous, scoreboard, objective);
            this.sessions.put(player.getUniqueId(), session);
            player.setScoreboard(scoreboard);
            this.updateNow(player, character, session);
         }
      }
   }

   public void update(Player player, CharacterProfile character) {
      if (this.settings.sidebarEnabled() && this.scoreboardSupported) {
         Session session = (Session)this.sessions.get(player.getUniqueId());
         if (session == null) {
            this.start(player, character);
         } else {
            long now = System.currentTimeMillis();
            if (now >= session.nextUpdateAtMillis) {
               this.updateNow(player, character, session);
            }
         }
      }
   }

   public void stop(Player player) {
      Session session = (Session)this.sessions.remove(player.getUniqueId());
      if (session != null) {
         if (player.getScoreboard() == session.scoreboard) {
            player.setScoreboard(session.previous);
         }

      }
   }

   public void shutdown() {
      this.sessions.clear();
   }

   private void updateNow(Player player, CharacterProfile character, Session session) {
      List<HudLine> lines = this.lines(player, character);
      List<String> keys = lines.stream().map(HudLine::cacheKey).toList();
      session.nextUpdateAtMillis = System.currentTimeMillis() + this.settings.sidebarUpdateIntervalTicks() * 50L;
      if (!keys.equals(session.lastLines)) {
         var var10000 = new HashSet<>(session.scoreboard.getEntries());
         Scoreboard var10001 = session.scoreboard;
         Objects.requireNonNull(var10001);
         var10000.forEach(var10001::resetScores);

         for(int index = 0; index < lines.size(); ++index) {
            HudLine line = (HudLine)lines.get(index);
            Score score = session.objective.getScore("rpgcore_line_" + index);
            score.setScore(lines.size() - index);
            score.customName(line.component());
            score.numberFormat(NumberFormat.blank());
         }

         session.lastLines = keys;
      }
   }

   private List<HudLine> lines(Player player, CharacterProfile character) {
      List<HudLine> lines = new ArrayList();
      Location location = player.getLocation();
      int var10000 = (int)Math.floor(location.getX());
      String coordinates = var10000 + ", " + (int)Math.floor(location.getY()) + ", " + (int)Math.floor(location.getZ());
      lines.add(this.line("coordinates:" + coordinates, Component.text("◆ ", NamedTextColor.GOLD).append(Component.text(coordinates, NamedTextColor.WHITE))));
      String world = player.getWorld().getName();
      lines.add(this.line("world:" + world, Component.text("  " + world, NamedTextColor.GRAY)));
      lines.add(this.line("blank:top", Component.empty()));
      lines.add(this.line("heading", Component.text("追蹤目標", NamedTextColor.GREEN)));
      QuestDefinition quest = (QuestDefinition)this.questRegistry.find(character.trackedQuestId()).orElse(null);
      QuestProgress progress = (QuestProgress)character.questProgress().get(character.trackedQuestId());
      if (quest != null && progress != null) {
         lines.add(this.line("quest:" + quest.id(), this.messages.text(quest.displayName())));
         int shown = 0;

         for(QuestObjectiveDefinition objective : quest.objectives()) {
            int current = (Integer)progress.objectiveProgress().getOrDefault(objective.id(), 0);
            if (current < objective.requiredAmount()) {
               String key = objective.id() + ":" + current + ":" + objective.requiredAmount();
               Component text = ((TextComponent)Component.text("- ", NamedTextColor.GOLD).append(Component.text(objective.description(), NamedTextColor.GRAY))).append(Component.text(" " + current + "/" + objective.requiredAmount(), NamedTextColor.WHITE));
               lines.add(this.line(key, text));
               ++shown;
               if (shown >= 3) {
                  break;
               }
            }
         }

         if (shown == 0) {
            lines.add(this.line("quest-complete", Component.text("目標已完成", NamedTextColor.AQUA)));
         }

         return lines;
      } else {
         lines.add(this.line("no-quest", Component.text("尚未追蹤任務", NamedTextColor.GRAY)));
         lines.add(this.line("open-book", Component.text("右鍵旅圖冊選擇", NamedTextColor.DARK_GRAY)));
         return lines;
      }
   }

   private HudLine line(String cacheKey, Component component) {
      return new HudLine(cacheKey, component);
   }

   private static record HudLine(String cacheKey, Component component) {
   }

   private static final class Session {
      private final Scoreboard previous;
      private final Scoreboard scoreboard;
      private final Objective objective;
      private volatile long nextUpdateAtMillis;
      private volatile List<String> lastLines = List.of();

      private Session(Scoreboard previous, Scoreboard scoreboard, Objective objective) {
         this.previous = previous;
         this.scoreboard = scoreboard;
         this.objective = objective;
      }
   }
}
