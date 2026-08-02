package fun.prof_chen.teamviewer.main_code.plugin;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/** Adds record deserialization support for Minecraft runtimes that still provide Gson 2.8.x. */
final class LegacyRecordTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();
        if (!rawType.isRecord()) return null;

        RecordComponent[] components = rawType.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        TypeAdapter<?>[] componentAdapters = new TypeAdapter<?>[components.length];
        Object[] defaults = new Object[components.length];
        Map<String, Integer> componentIndexes = new HashMap<>();

        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            parameterTypes[index] = component.getType();
            componentAdapters[index] = gson.getAdapter(TypeToken.get(component.getGenericType()));
            defaults[index] = primitiveDefault(component.getType());
            componentIndexes.put(component.getName(), index);
        }

        Constructor<?> constructor;
        try {
            constructor = rawType.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalArgumentException("Unable to access record constructor for " + rawType.getName(), error);
        }

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        return new TypeAdapter<>() {
            @Override
            public T read(JsonReader input) throws IOException {
                if (input.peek() == JsonToken.NULL) {
                    input.nextNull();
                    return null;
                }

                Object[] arguments = defaults.clone();
                input.beginObject();
                while (input.hasNext()) {
                    Integer index = componentIndexes.get(input.nextName());
                    if (index == null) {
                        input.skipValue();
                        continue;
                    }
                    Object value = componentAdapters[index].read(input);
                    arguments[index] = value == null && parameterTypes[index].isPrimitive()
                            ? defaults[index] : value;
                }
                input.endObject();

                try {
                    @SuppressWarnings("unchecked")
                    T record = (T) constructor.newInstance(arguments);
                    return record;
                } catch (ReflectiveOperationException error) {
                    throw new IOException("Unable to construct record " + rawType.getName(), error);
                }
            }

            @Override
            public void write(JsonWriter output, T value) throws IOException {
                if (value == null) {
                    output.nullValue();
                    return;
                }
                delegate.write(output, value);
            }
        };
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unknown primitive type " + type.getName());
    }
}
