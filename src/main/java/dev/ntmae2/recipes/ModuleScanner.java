package dev.ntmae2.recipes;

import com.hbm.module.machine.ModuleMachineBase;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ModuleScanner {

    private static final Map<Class<?>, Field[]> CACHE = new ConcurrentHashMap<>();

    private ModuleScanner() {}

    static boolean isRecipeMachineClass(Class<?> type) {
        if (!type.getName().startsWith("com.hbm.")) return false;
        return fields(type).length > 0;
    }

    static List<ModuleMachineBase> modules(Object blockEntity) {
        List<ModuleMachineBase> out = new ArrayList<>();
        for (Field field : fields(blockEntity.getClass())) {
            try {
                Object value = field.get(blockEntity);
                if (value instanceof ModuleMachineBase module) {
                    out.add(module);
                } else if (value instanceof ModuleMachineBase[] array) {
                    for (ModuleMachineBase module : array) if (module != null) out.add(module);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                
            }
        }
        return out;
    }

    private static Field[] fields(Class<?> type) {
        return CACHE.computeIfAbsent(type, ModuleScanner::scan);
    }

    private static Field[] scan(Class<?> type) {
        List<Field> found = new ArrayList<>();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                Class<?> t = f.getType();
                boolean single = ModuleMachineBase.class.isAssignableFrom(t);
                boolean array = t.isArray() && ModuleMachineBase.class.isAssignableFrom(t.getComponentType());
                if (!single && !array) continue;
                try {
                    f.setAccessible(true);
                    found.add(f);
                } catch (RuntimeException e) {
                    RecipeSwitchMod.LOGGER.warn("Cannot access module field {} on {}", f.getName(), k.getName());
                }
            }
        }
        return found.toArray(new Field[0]);
    }
}