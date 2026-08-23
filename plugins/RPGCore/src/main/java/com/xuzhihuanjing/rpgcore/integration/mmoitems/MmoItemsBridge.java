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
        Object service = provider().orElse(null);
        if (service == null) {
            return false;
        }
        try {
            return invoke(service, "definitions", new Class<?>[0]) instanceof Iterable<?>;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            warn("AelornItems service API is registered but cannot be called: " + rootMessage(exception));
            return false;
        }
    }

    public Optional<Identity> inspect(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        try {
            Object service = provider().orElse(null);
            if (service == null) {
                return Optional.empty();
            }
            Object result = invoke(service, "inspect", new Class<?>[] { ItemStack.class }, item);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return Optional.empty();
            }
            Object identity = optional.get();
            return Optional.of(new Identity(
                    string(identity, "type"),
                    string(identity, "id"),
                    string(identity, "tier"),
                    number(identity, "level").intValue(),
                    string(identity, "requiredClass"),
                    number(identity, "requiredLevel").intValue(),
                    stats(identity),
                    integerMap(identity, "skillRequirements"),
                    stringList(identity, "questRequirements"),
                    optionalString(identity, "majorIdentification")));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            warn("Could not inspect AelornItems item through its service API: " + rootMessage(exception));
            return Optional.empty();
        }
    }

    private Optional<Object> provider() {
        Plugin plugin = pluginManager.getPlugin("AelornItems");
        if (plugin == null || !plugin.isEnabled()) {
            return Optional.empty();
        }
        try {
            Class<?> apiType = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiType);
            return registration == null ? Optional.empty() : Optional.ofNullable(registration.getProvider());
        } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
            warn("AelornItems service API is not ready: " + rootMessage(exception));
            return Optional.empty();
        }
    }

    private Map<String, Double> stats(Object identity) throws ReflectiveOperationException {
        Object raw = invoke(identity, "stats", new Class<?>[0]);
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Double> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key
                    && entry.getValue() instanceof Number value
                    && Double.isFinite(value.doubleValue())) {
                converted.put(key, value.doubleValue());
            }
        }
        return Map.copyOf(converted);
    }

    private Map<String, Integer> integerMap(Object identity, String method) throws ReflectiveOperationException {
        Object raw = optionalInvoke(identity, method);
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof Number value) {
                converted.put(key, Math.max(0, value.intValue()));
            }
        }
        return Map.copyOf(converted);
    }

    private List<String> stringList(Object identity, String method) throws ReflectiveOperationException {
        Object raw = optionalInvoke(identity, method);
        if (!(raw instanceof Iterable<?> values)) {
            return List.of();
        }
        ArrayList<String> converted = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                converted.add(value.toString());
            }
        }
        return List.copyOf(converted);
    }

    private String optionalString(Object identity, String method) throws ReflectiveOperationException {
        Object value = optionalInvoke(identity, method);
        return value == null ? "" : value.toString();
    }

    private Object optionalInvoke(Object target, String method) throws ReflectiveOperationException {
        try {
            return invoke(target, method, new Class<?>[0]);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private String string(Object target, String method) throws ReflectiveOperationException {
        Object value = invoke(target, method, new Class<?>[0]);
        return value == null ? "" : value.toString();
    }

    private Number number(Object target, String method) throws ReflectiveOperationException {
        Object value = invoke(target, method, new Class<?>[0]);
        return value instanceof Number number ? number : 0;
    }

    private Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        try {
            Method reflected = publicContractMethod(target, method, parameterTypes);
            return reflected.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }

    static Method publicContractMethod(Object target, String method, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Class<?> contract : current.getInterfaces()) {
                try {
                    Method candidate = contract.getMethod(method, parameterTypes);
                    if (Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) {
                        return candidate;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try the next public contract.
                }
            }
        }
        return target.getClass().getMethod(method, parameterTypes);
    }

    private void warn(String message) {
        if (!warned) {
            warned = true;
            logger.warning(message);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    public record Identity(
            String type,
            String id,
            String tier,
            int level,
            String requiredClass,
            int requiredLevel,
            Map<String, Double> stats,
            Map<String, Integer> skillRequirements,
            List<String> questRequirements,
            String majorIdentification) {

        public Identity {
            stats = Map.copyOf(stats);
            skillRequirements = Map.copyOf(skillRequirements);
            questRequirements = List.copyOf(questRequirements);
        }

        public String objectiveTarget() {
            return type.toUpperCase(Locale.ROOT) + ":" + id.toUpperCase(Locale.ROOT);
        }
    }
}
