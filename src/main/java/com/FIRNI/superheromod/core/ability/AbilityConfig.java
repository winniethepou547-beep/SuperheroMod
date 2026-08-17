package com.FIRNI.superheromod.core.ability;

import java.util.HashMap;
import java.util.Map;

public class AbilityConfig {

    private final Map<String, Object> values = new HashMap<>();

    public AbilityConfig set(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public int getInt(String key, int defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).intValue() : defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : defaultValue;
    }

    public boolean getBool(String key, boolean defaultValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).longValue() : defaultValue;
    }
}
