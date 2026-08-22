package com.xuzhihuanjing.rpgcore.monster;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.PluginManager;

public final class ModelEngineBridge {
   private static final String API_CLASS = "com.ticxo.modelengine.api.ModelEngineAPI";
   private final PluginManager pluginManager;
   private final Logger logger;
   private final Set<String> warnings = ConcurrentHashMap.newKeySet();

   public ModelEngineBridge(PluginManager pluginManager, Logger logger) {
      this.pluginManager = pluginManager;
      this.logger = logger;
   }

   public boolean available() {
      return this.pluginManager.isPluginEnabled("ModelEngine") && this.apiClass().isPresent();
   }

   public boolean attach(LivingEntity entity, String modelId) {
      if (entity != null && modelId != null && !modelId.isBlank() && this.available()) {
         try {
            Class<?> api = (Class)this.apiClass().orElseThrow(ClassNotFoundException::new);
            Object modeledEntity = this.invokeStatic(api, "getOrCreateModeledEntity", entity);
            if (modeledEntity == null) {
               this.warnOnce("modeled-entity", "ModelEngine could not create a modeled entity");
               return false;
            } else {
               Object existing = this.optionalValue(this.invokeNamed(modeledEntity, "getModel", modelId));
               if (existing == null) {
                  Object activeModel = this.invokeStatic(api, "createActiveModel", modelId);
                  if (activeModel == null) {
                     this.warnOnce("missing-model:" + modelId, "ModelEngine model is not loaded: " + modelId);
                     return false;
                  }

                  existing = this.optionalValue(this.invokeNamed(modeledEntity, "addModel", activeModel, true));
                  if (existing == null) {
                     this.warnOnce("attach-failed:" + modelId, "ModelEngine rejected model attachment: " + modelId);
                     return false;
                  }
               }

               this.invokeNamed(modeledEntity, "setBaseEntityVisible", false);
               return true;
            }
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            this.warnFailure("attach:" + modelId, "Could not attach ModelEngine model " + modelId, exception);
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean play(LivingEntity entity, String modelId, String animation) {
      if (entity != null && modelId != null && !modelId.isBlank() && animation != null && !animation.isBlank() && this.available()) {
         try {
            Class<?> api = (Class)this.apiClass().orElseThrow(ClassNotFoundException::new);
            Object modeledEntity = this.invokeStatic(api, "getModeledEntity", entity);
            if (modeledEntity == null) {
               return false;
            } else {
               Object activeModel = this.optionalValue(this.invokeNamed(modeledEntity, "getModel", modelId));
               if (activeModel == null) {
                  return false;
               } else {
                  Object handler = this.invokeNamed(activeModel, "getAnimationHandler");
                  if (handler == null) {
                     return false;
                  } else {
                     Object result = this.invokeNamed(handler, "playAnimation", animation, 0.1, 0.1, (double)1.0F, true);
                     boolean var10000;
                     if (result instanceof Boolean) {
                        Boolean accepted = (Boolean)result;
                        if (!accepted) {
                           var10000 = false;
                           return var10000;
                        }
                     }

                     var10000 = true;
                     return var10000;
                  }
               }
            }
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            this.warnFailure("animation:" + modelId + ":" + animation, "Could not play ModelEngine animation " + modelId + "/" + animation, exception);
            return false;
         }
      } else {
         return false;
      }
   }

   public void markHurt(LivingEntity entity) {
      if (entity != null && this.available()) {
         try {
            Class<?> api = (Class)this.apiClass().orElseThrow(ClassNotFoundException::new);
            Object modeledEntity = this.invokeStatic(api, "getModeledEntity", entity);
            if (modeledEntity != null) {
               this.invokeNamed(modeledEntity, "markHurt");
            }
         } catch (LinkageError | RuntimeException | ReflectiveOperationException exception) {
            this.warnFailure("mark-hurt", "Could not mark a ModelEngine entity as hurt", exception);
         }

      }
   }

   private Optional<Class<?>> apiClass() {
      try {
         return Optional.of(Class.forName("com.ticxo.modelengine.api.ModelEngineAPI"));
      } catch (ClassNotFoundException var2) {
         return Optional.empty();
      }
   }

   private Object invokeStatic(Class<?> owner, String name, Object... arguments) throws ReflectiveOperationException {
      Method method = this.compatibleMethod(owner, name, arguments, true);
      return this.invoke(method, (Object)null, arguments);
   }

   private Object invokeNamed(Object owner, String name, Object... arguments) throws ReflectiveOperationException {
      Method method = this.compatibleMethod(owner.getClass(), name, arguments, false);
      return this.invoke(method, owner, arguments);
   }

   private Method compatibleMethod(Class<?> owner, String name, Object[] arguments, boolean requireStatic) throws NoSuchMethodException {
      return (Method)Arrays.stream(owner.getMethods()).filter((method) -> method.getName().equals(name)).filter((method) -> method.getParameterCount() == arguments.length).filter((method) -> !requireStatic || Modifier.isStatic(method.getModifiers())).filter((method) -> this.parametersMatch(method.getParameterTypes(), arguments)).findFirst().orElseThrow(() -> new NoSuchMethodException(owner.getName() + "#" + name + "/" + arguments.length));
   }

   private boolean parametersMatch(Class<?>[] parameterTypes, Object[] arguments) {
      for(int index = 0; index < parameterTypes.length; ++index) {
         Object argument = arguments[index];
         if (argument == null) {
            if (parameterTypes[index].isPrimitive()) {
               return false;
            }
         } else if (!this.wrap(parameterTypes[index]).isInstance(argument)) {
            return false;
         }
      }

      return true;
   }

   private Class<?> wrap(Class<?> type) {
      if (!type.isPrimitive()) {
         return type;
      } else if (type == Boolean.TYPE) {
         return Boolean.class;
      } else if (type == Integer.TYPE) {
         return Integer.class;
      } else if (type == Long.TYPE) {
         return Long.class;
      } else if (type == Double.TYPE) {
         return Double.class;
      } else if (type == Float.TYPE) {
         return Float.class;
      } else if (type == Short.TYPE) {
         return Short.class;
      } else if (type == Byte.TYPE) {
         return Byte.class;
      } else {
         return type == Character.TYPE ? Character.class : type;
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

   private Object optionalValue(Object value) {
      Object var10000;
      if (value instanceof Optional<?> optional) {
         var10000 = optional.orElse(null);
      } else {
         var10000 = value;
      }

      return var10000;
   }

   private void warnOnce(String key, String message) {
      if (this.warnings.add(key)) {
         this.logger.warning(message);
      }

   }

   private void warnFailure(String key, String message, Throwable exception) {
      if (this.warnings.add(key)) {
         this.logger.log(Level.WARNING, message, exception);
      }

   }
}
