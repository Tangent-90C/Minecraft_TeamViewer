package fun.prof_chen.teamviewer.main_code.plugin;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class LuaValueConverters {
    private LuaValueConverters() { }

    static LuaValue toLua(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (value instanceof Integer integer) return LuaValue.valueOf(integer);
        if (value instanceof Long number) return LuaValue.valueOf(number);
        if (value instanceof Number number) return LuaValue.valueOf(number.doubleValue());
        if (value instanceof String string) return LuaValue.valueOf(string);
        if (value instanceof UUID uuid) return LuaValue.valueOf(uuid.toString());
        if (value instanceof Enum<?> enumeration) return LuaValue.valueOf(enumeration.name());
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            map.forEach((key, item) -> table.set(String.valueOf(key), toLua(item)));
            return table;
        }
        if (value instanceof Iterable<?> iterable) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (Object item : iterable) table.set(index++, toLua(item));
            return table;
        }
        if (value.getClass().isArray()) {
            LuaTable table = new LuaTable();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                table.set(index + 1, toLua(java.lang.reflect.Array.get(value, index)));
            }
            return table;
        }
        if (value.getClass().isRecord()) {
            LuaTable table = new LuaTable();
            for (java.lang.reflect.RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    table.set(component.getName(), toLua(component.getAccessor().invoke(value)));
                } catch (ReflectiveOperationException error) {
                    throw new IllegalArgumentException(
                            "Unable to expose record component " + component.getName(), error);
                }
            }
            return table;
        }
        return CoerceJavaToLua.coerce(value);
    }

    static LuaTable settings(Map<String, Object> values) {
        LuaTable table = new LuaTable();
        values.forEach((key, value) -> table.set(key, toLua(value)));
        return table;
    }

    static Map<String, Object> stringObjectMap(LuaValue value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!value.istable()) return result;
        LuaValue key = LuaValue.NIL;
        while (true) {
            var next = value.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            result.put(key.tojstring(), toJava(next.arg(2)));
        }
        return result;
    }

    static Object toJava(LuaValue value) {
        if (value == null || value.isnil()) return null;
        if (value.isboolean()) return value.toboolean();
        if (value.isint()) return value.toint();
        if (value.isnumber()) return value.todouble();
        if (value.isstring()) return value.tojstring();
        if (value.isuserdata()) return value.touserdata();
        if (value.istable()) return stringObjectMap(value);
        return value.tojstring();
    }
}
