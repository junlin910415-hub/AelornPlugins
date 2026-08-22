package com.xuzhihuanjing.rpgcore.monster;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.PluginManager;

public final class MythicMobsBridge {
   private final PluginManager pluginManager;
   private final Logger logger;
   private boolean warnedUnavailable;

   public MythicMobsBridge(PluginManager pluginManager, Logger logger) {
      this.pluginManager = pluginManager;
      this.logger = logger;
   }

   public boolean available() {
      return this.pluginManager.isPluginEnabled("MythicMobs") && this.mythicBukkitClass().isPresent();
   }

   public Optional<LivingEntity> spawn(String mythicMobId, Location location, int level) {
      try {
         Object mythic = this.mythicBukkit();
         Object manager = mythic.getClass().getMethod("getMobManager").invoke(mythic);
         Optional<Object> spawnResult = this.spawnViaManager(manager, mythicMobId, location, level);
         if (spawnResult.isEmpty()) {
            spawnResult = this.spawnViaMythicMob(manager, mythicMobId, location, level);
         }

         Object activeMob = spawnResult.orElse(null);
         if (activeMob == null) {
            this.warn("Could not find a compatible MythicMobs spawn method for " + mythicMobId);
            return Optional.empty();
         } else {
            this.setLevel(activeMob, level);
            var var10000 = this.extractBukkitEntity(activeMob);
            Objects.requireNonNull(LivingEntity.class);
            var10000 = var10000.filter(LivingEntity.class::isInstance);
            Objects.requireNonNull(LivingEntity.class);
            return var10000.map(LivingEntity.class::cast);
         }
      } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
         this.logger.log(Level.WARNING, "Could not spawn MythicMobs mob " + mythicMobId, exception);
         return Optional.empty();
      }
   }

   public boolean isMythicMob(Entity entity, String mythicMobId) {
      return mythicMobId != null && !mythicMobId.isBlank() ? (Boolean)this.mythicMobId(entity).map((id) -> id.equalsIgnoreCase(mythicMobId)).orElse(false) : false;
   }

   public Optional<String> mythicMobId(Entity entity) {
      if (entity != null && this.available()) {
         try {
            Object mythic = this.mythicBukkit();
            Object manager = mythic.getClass().getMethod("getMobManager").invoke(mythic);
            Object activeMob = this.findActiveMob(manager, entity.getUniqueId()).orElse(null);
            return activeMob == null ? Optional.empty() : this.readMythicMobId(activeMob);
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            this.warn("Could not inspect MythicMobs entity identity: " + ((Throwable)exception).getMessage());
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private Optional<Object> findActiveMob(Object manager, UUID entityId) throws ReflectiveOperationException {
      for(String methodName : MythicMobsBridge.ListMethods.ACTIVE_MOB_LOOKUP) {
         for(Method method : manager.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
               Class<?> parameterType = method.getParameterTypes()[0];
               Object argument;
               if (parameterType.isAssignableFrom(UUID.class)) {
                  argument = entityId;
               } else {
                  if (!parameterType.isAssignableFrom(String.class)) {
                     continue;
                  }

                  argument = entityId.toString();
               }

               Object result = this.invoke(method, manager, argument);
               if (result instanceof Optional) {
                  Optional<?> optional = (Optional)result;
                  return optional.map((value) -> value);
               }

               if (result != null) {
                  return Optional.of(result);
               }
            }
         }
      }

      return Optional.empty();
   }

   private Optional<String> readMythicMobId(Object activeMob) throws ReflectiveOperationException {
      for(String methodName : MythicMobsBridge.ListMethods.MOB_ID_ACCESSORS) {
         Method method = (Method)Arrays.stream(activeMob.getClass().getMethods()).filter((candidate) -> candidate.getName().equals(methodName) && candidate.getParameterCount() == 0).findFirst().orElse(null);
         if (method != null) {
            Object value = this.invoke(method, activeMob);
            if (value instanceof String) {
               String id = (String)value;
               if (!id.isBlank()) {
                  return Optional.of(id);
               }
            }

            if (value != null) {
               Method internalName = (Method)Arrays.stream(value.getClass().getMethods()).filter((candidate) -> candidate.getName().equals("getInternalName") && candidate.getParameterCount() == 0).findFirst().orElse(null);
               if (internalName != null) {
                  Object id = this.invoke(internalName, value);
                  if (id instanceof String) {
                     String name = (String)id;
                     if (!name.isBlank()) {
                        return Optional.of(name);
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private Optional<Object> spawnViaManager(Object manager, String mythicMobId, Location location, int level) throws ReflectiveOperationException {
      for(Method method : manager.getClass().getMethods()) {
         if (method.getName().equals("spawnMob")) {
            Object[] arguments = this.spawnArguments(method, mythicMobId, location, level);
            if (arguments != null) {
               return Optional.ofNullable(this.invoke(method, manager, arguments));
            }
         }
      }

      return Optional.empty();
   }

   private Optional<Object> spawnViaMythicMob(Object manager, String mythicMobId, Location location, int level) throws ReflectiveOperationException {
      Object mythicMob = this.lookupMythicMob(manager, mythicMobId).orElse(null);
      if (mythicMob == null) {
         this.warn("MythicMobs mob is not loaded: " + mythicMobId);
         return Optional.empty();
      } else {
         for(Method method : mythicMob.getClass().getMethods()) {
            if (method.getName().equals("spawn")) {
               Object[] arguments = this.spawnArguments(method, mythicMobId, location, level);
               if (arguments != null) {
                  return Optional.ofNullable(this.invoke(method, mythicMob, arguments));
               }
            }
         }

         return Optional.empty();
      }
   }

   private Optional<Object> lookupMythicMob(Object manager, String mythicMobId) throws ReflectiveOperationException {
      for(String methodName : MythicMobsBridge.ListMethods.MYTHIC_MOB_LOOKUP) {
         for(Method method : manager.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(String.class)) {
               Object result = this.invoke(method, manager, mythicMobId);
               if (result instanceof Optional) {
                  Optional<?> optional = (Optional)result;
                  return optional.map((value) -> value);
               }

               return Optional.ofNullable(result);
            }
         }
      }

      return Optional.empty();
   }

   private Object[] spawnArguments(Method method, String mythicMobId, Location location, int level) throws ReflectiveOperationException {
      Class<?>[] types = method.getParameterTypes();
      if (types.length >= 1 && types.length <= 3) {
         Object[] arguments = new Object[types.length];
         int index = 0;
         if (types[0].isAssignableFrom(String.class)) {
            arguments[index++] = mythicMobId;
         }

         if (index >= types.length) {
            return null;
         } else {
            Object adaptedLocation = this.adaptLocation(location, types[index]).orElse(null);
            if (adaptedLocation == null) {
               return null;
            } else {
               arguments[index++] = adaptedLocation;
               if (index < types.length) {
                  Object levelArgument = this.adaptNumber(level, types[index]);
                  if (levelArgument == null) {
                     return null;
                  }

                  arguments[index] = levelArgument;
               }

               return arguments;
            }
         }
      } else {
         return null;
      }
   }

   private Optional<Object> adaptLocation(Location location, Class<?> expectedType) throws ReflectiveOperationException {
      return expectedType.isInstance(location) ? Optional.of(location) : this.adaptWithBukkitAdapter(location, expectedType);
   }

   private Optional<Object> adaptWithBukkitAdapter(Object value, Class<?> expectedType) throws ReflectiveOperationException {
      Class<?> adapter = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");

      for(Method method : adapter.getMethods()) {
         if (Modifier.isStatic(method.getModifiers()) && method.getName().equals("adapt") && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(value) && expectedType.isAssignableFrom(method.getReturnType())) {
            return Optional.ofNullable(this.invoke(method, (Object)null, value));
         }
      }

      return Optional.empty();
   }

   private Optional<Entity> extractBukkitEntity(Object value) throws ReflectiveOperationException {
      if (value == null) {
         return Optional.empty();
      } else if (value instanceof Optional) {
         Optional<?> optional = (Optional)value;
         return optional.isPresent() ? this.extractBukkitEntity(optional.get()) : Optional.empty();
      } else if (value instanceof Entity) {
         Entity entity = (Entity)value;
         return Optional.of(entity);
      } else {
         for(String methodName : MythicMobsBridge.ListMethods.ENTITY_ACCESSORS) {
            Method method = (Method)Arrays.stream(value.getClass().getMethods()).filter((candidate) -> candidate.getName().equals(methodName) && candidate.getParameterCount() == 0).findFirst().orElse(null);
            if (method != null) {
               Optional<Entity> entity = this.extractBukkitEntity(this.invoke(method, value));
               if (entity.isPresent()) {
                  return entity;
               }
            }
         }

         var var10000 = this.adaptWithBukkitAdapter(value, Entity.class);
         Objects.requireNonNull(Entity.class);
         var10000 = var10000.filter(Entity.class::isInstance);
         Objects.requireNonNull(Entity.class);
         return var10000.map(Entity.class::cast);
      }
   }

   private void setLevel(Object value, int level) throws ReflectiveOperationException {
      if (value != null) {
         if (value instanceof Optional) {
            Optional<?> optional = (Optional)value;
            if (optional.isPresent()) {
               this.setLevel(optional.get(), level);
            }

         } else {
            for(Method method : value.getClass().getMethods()) {
               if (method.getName().equals("setLevel") && method.getParameterCount() == 1) {
                  Object argument = this.adaptNumber(level, method.getParameterTypes()[0]);
                  if (argument != null) {
                     this.invoke(method, value, argument);
                     return;
                  }
               }
            }

         }
      }
   }

   private Object adaptNumber(int value, Class<?> targetType) {
      if (targetType != Integer.TYPE && targetType != Integer.class) {
         if (targetType != Double.TYPE && targetType != Double.class) {
            if (targetType != Float.TYPE && targetType != Float.class) {
               return targetType != Long.TYPE && targetType != Long.class ? null : (long)value;
            } else {
               return (float)value;
            }
         } else {
            return (double)value;
         }
      } else {
         return value;
      }
   }

   private Object mythicBukkit() throws ReflectiveOperationException {
      Class<?> type = (Class)this.mythicBukkitClass().orElseThrow(ClassNotFoundException::new);
      return type.getMethod("inst").invoke((Object)null);
   }

   private Optional<Class<?>> mythicBukkitClass() {
      try {
         return Optional.of(Class.forName("io.lumine.mythic.bukkit.MythicBukkit"));
      } catch (ClassNotFoundException var2) {
         if (!this.warnedUnavailable) {
            this.warnedUnavailable = true;
            Bukkit.getLogger().warning("MythicMobs is not available to RPGCore");
         }

         return Optional.empty();
      }
   }

   private Object invoke(Method method, Object owner, Object... arguments) throws ReflectiveOperationException {
      try {
         return method.invoke(owner, arguments);
      } catch (InvocationTargetException exception) {
         Throwable cause = exception.getCause();
         if (cause instanceof ReflectiveOperationException reflective) {
            throw reflective;
         } else if (cause instanceof RuntimeException runtime) {
            throw runtime;
         } else {
            throw exception;
         }
      }
   }

   private void warn(String message) {
      if (!this.warnedUnavailable) {
         this.warnedUnavailable = true;
         this.logger.warning(message);
      }

   }

   private static final class ListMethods {
      private static final String[] MYTHIC_MOB_LOOKUP = new String[]{"getMythicMob", "getMobType", "getMob"};
      private static final String[] ENTITY_ACCESSORS = new String[]{"getEntity", "getBukkitEntity", "getLivingEntity"};
      private static final String[] ACTIVE_MOB_LOOKUP = new String[]{"getSkillCaster", "getActiveMob", "getActiveMobByUUID"};
      private static final String[] MOB_ID_ACCESSORS = new String[]{"getType", "getMobType"};
   }
}
