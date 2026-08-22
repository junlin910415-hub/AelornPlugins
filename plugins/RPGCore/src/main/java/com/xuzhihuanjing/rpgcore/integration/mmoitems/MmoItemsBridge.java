package com.xuzhihuanjing.rpgcore.integration.mmoitems;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class MmoItemsBridge {
   private static final String API_CLASS = "tw.linsy.aelorn.mmoitems.api.MMOItemsApi";
   private final PluginManager pluginManager;
   private final Logger logger;
   private boolean warned;

   public MmoItemsBridge(PluginManager pluginManager, Logger logger) {
      this.pluginManager = pluginManager;
      this.logger = logger;
   }

   public boolean available() {
      Object service = this.provider().orElse(null);
      if (service == null) {
         return false;
      } else {
         try {
            return this.invoke(service, "definitions", new Class[0]) instanceof Iterable;
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            String var10001 = this.rootMessage(exception);
            this.warn("AelornItems service API is registered but cannot be called: " + var10001);
            return false;
         }
      }
   }

   public Optional<Identity> inspect(ItemStack item) {
      if (item != null && !item.getType().isAir()) {
         try {
            Object provider = this.provider().orElse(null);
            if (provider == null) {
               return Optional.empty();
            } else {
               Object result = this.invoke(provider, "inspect", new Class[]{ItemStack.class}, item);
               if (result instanceof Optional) {
                  Optional<?> optional = (Optional)result;
                  if (!optional.isEmpty()) {
                     Object identity = optional.get();
                     return Optional.of(new Identity(this.string(identity, "type"), this.string(identity, "id"), this.string(identity, "tier"), this.number(identity, "level").intValue(), this.string(identity, "requiredClass"), this.number(identity, "requiredLevel").intValue(), this.stats(identity), this.integerMap(identity, "skillRequirements"), this.stringList(identity, "questRequirements"), this.optionalString(identity, "majorIdentification")));
                  }
               }

               return Optional.empty();
            }
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            String var10001 = this.rootMessage(exception);
            this.warn("Could not inspect AelornItems item through its service API: " + var10001);
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private Optional<Object> provider() {
      Plugin plugin = this.pluginManager.getPlugin("AelornItems");
      if (plugin != null && plugin.isEnabled()) {
         try {
            Class<?> apiType = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiType);
            return registration == null ? Optional.empty() : Optional.ofNullable(registration.getProvider());
         } catch (LinkageError | RuntimeException | ClassNotFoundException exception) {
            String var10001 = this.rootMessage(exception);
            this.warn("AelornItems service API is not ready: " + var10001);
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private Map<String, Double> stats(Object identity) throws ReflectiveOperationException {
      Object raw = this.invoke(identity, "stats", new Class[0]);
      if (raw instanceof Map<?, ?> map) {
         LinkedHashMap converted = new LinkedHashMap();

         for(Map.Entry<?, ?> entry : map.entrySet()) {
            Object var9 = entry.getKey();
            if (var9 instanceof String key) {
               var9 = entry.getValue();
               if (var9 instanceof Number value) {
                  if (Double.isFinite(value.doubleValue())) {
                     converted.put(key, value.doubleValue());
                  }
               }
            }
         }

         return converted;
      } else {
         return Map.of();
      }
   }

   private Map<String, Integer> integerMap(Object identity, String method) throws ReflectiveOperationException {
      Object raw = this.optionalInvoke(identity, method);
      if (raw instanceof Map<?, ?> map) {
         LinkedHashMap converted = new LinkedHashMap();

         for(Map.Entry<?, ?> entry : map.entrySet()) {
            Object var10 = entry.getKey();
            if (var10 instanceof String key) {
               var10 = entry.getValue();
               if (var10 instanceof Number value) {
                  converted.put(key, Math.max(0, value.intValue()));
               }
            }
         }

         return converted;
      } else {
         return Map.of();
      }
   }

   private List<String> stringList(Object identity, String method) throws ReflectiveOperationException {
      Object raw = this.optionalInvoke(identity, method);
      if (raw instanceof Iterable<?> values) {
         ArrayList converted = new ArrayList();

         for(Object value : values) {
            if (value != null && !value.toString().isBlank()) {
               converted.add(value.toString());
            }
         }

         return List.copyOf(converted);
      } else {
         return List.of();
      }
   }

   private String optionalString(Object identity, String method) throws ReflectiveOperationException {
      Object value = this.optionalInvoke(identity, method);
      return value == null ? "" : value.toString();
   }

   private Object optionalInvoke(Object target, String method) throws ReflectiveOperationException {
      try {
         return this.invoke(target, method, new Class[0]);
      } catch (NoSuchMethodException var4) {
         return null;
      }
   }

   private String string(Object target, String method) throws ReflectiveOperationException {
      Object value = this.invoke(target, method, new Class[0]);
      return value == null ? "" : value.toString();
   }

   private Number number(Object target, String method) throws ReflectiveOperationException {
      Object value = this.invoke(target, method, new Class[0]);
      Object var10000;
      if (value instanceof Number number) {
         var10000 = number;
      } else {
         var10000 = 0;
      }

      return (Number)var10000;
   }

   private Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... arguments) throws ReflectiveOperationException {
      try {
         Method reflected = publicContractMethod(target, method, parameterTypes);
         return reflected.invoke(target, arguments);
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

   static Method publicContractMethod(Object target, String method, Class<?>[] parameterTypes) throws NoSuchMethodException {
      for(Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
         for(Class<?> contract : current.getInterfaces()) {
            try {
               Method candidate = contract.getMethod(method, parameterTypes);
               if (Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) {
                  return candidate;
               }
            } catch (NoSuchMethodException var9) {
            }
         }
      }

      return target.getClass().getMethod(method, parameterTypes);
   }

   private void warn(String message) {
      if (!this.warned) {
         this.warned = true;
         this.logger.warning(message);
      }
   }

   private String rootMessage(Throwable throwable) {
      Throwable var10000;
      label21: {
         if (throwable instanceof InvocationTargetException invocation) {
            if (invocation.getCause() != null) {
               var10000 = invocation.getCause();
               break label21;
            }
         }

         var10000 = throwable;
      }

      Throwable root = var10000;
      String message = root.getMessage();
      return message != null && !message.isBlank() ? message : root.getClass().getSimpleName();
   }

   public static record Identity(String type, String id, String tier, int level, String requiredClass, int requiredLevel, Map<String, Double> stats, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification) {
      public Identity(String type, String id, String tier, int level, String requiredClass, int requiredLevel, Map<String, Double> stats, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification) {
         stats = Map.copyOf(stats);
         skillRequirements = Map.copyOf(skillRequirements);
         questRequirements = List.copyOf(questRequirements);
         this.type = type;
         this.id = id;
         this.tier = tier;
         this.level = level;
         this.requiredClass = requiredClass;
         this.requiredLevel = requiredLevel;
         this.stats = stats;
         this.skillRequirements = skillRequirements;
         this.questRequirements = questRequirements;
         this.majorIdentification = majorIdentification;
      }

      public String objectiveTarget() {
         String var10000 = this.type.toUpperCase(Locale.ROOT);
         return var10000 + ":" + this.id.toUpperCase(Locale.ROOT);
      }
   }
}
