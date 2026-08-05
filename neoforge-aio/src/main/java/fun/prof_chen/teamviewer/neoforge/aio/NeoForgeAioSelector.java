package fun.prof_chen.teamviewer.neoforge.aio;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

final class NeoForgeAioSelector {
    private static volatile NeoForgeAioTarget selected;

    private NeoForgeAioSelector() { }

    static NeoForgeAioTarget currentTarget() {
        NeoForgeAioTarget current = selected;
        if (current != null) return current;
        synchronized (NeoForgeAioSelector.class) {
            if (selected == null) {
                selected = targetFor(versionValue("mcVersion"), versionValue("neoForgeVersion"));
            }
            return selected;
        }
    }

    static NeoForgeAioTarget targetFor(String minecraftVersion) {
        List<NeoForgeAioTarget> matches = matchingMinecraftTargets(minecraftVersion);
        if (matches.size() != 1) {
            throw selectionFailure(minecraftVersion, null, matches);
        }
        return matches.get(0);
    }

    static NeoForgeAioTarget targetFor(String minecraftVersion, String neoForgeVersion) {
        List<NeoForgeAioTarget> matches = matchingMinecraftTargets(minecraftVersion).stream()
                .filter(target -> contains(target.neoForgeRange(), neoForgeVersion))
                .toList();
        if (matches.size() != 1) {
            throw selectionFailure(minecraftVersion, neoForgeVersion, matches);
        }
        return matches.get(0);
    }

    private static List<NeoForgeAioTarget> matchingMinecraftTargets(String minecraftVersion) {
        return NeoForgeAioTargets.ALL.stream()
                .filter(target -> contains(target.minecraftRange(), minecraftVersion))
                .toList();
    }

    static boolean isClient() {
        try {
            return isClient(Class.forName("net.neoforged.fml.loading.FMLLoader"));
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Cannot find the NeoForge loader", failure);
        }
    }

    static boolean isClient(Class<?> loader) {
        try {
            Method getDist = loader.getMethod("getDist");
            Object dist;
            if (Modifier.isStatic(getDist.getModifiers())) {
                dist = getDist.invoke(null);
            } else {
                Object current = loader.getMethod("getCurrent").invoke(null);
                dist = getDist.invoke(current);
            }
            return "CLIENT".equals(String.valueOf(dist));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot determine the NeoForge distribution", failure);
        }
    }

    static void launchAdapter(Object modBus, Object container) {
        NeoForgeAioTarget target = currentTarget();
        try {
            Class<?> entrypoint = Class.forName(target.entrypoint(), true,
                    Thread.currentThread().getContextClassLoader());
            Constructor<?> constructor = Arrays.stream(entrypoint.getConstructors())
                    .filter(candidate -> candidate.getParameterCount() == 2)
                    .filter(candidate -> candidate.getParameterTypes()[0].isInstance(modBus)
                            && candidate.getParameterTypes()[1].isInstance(container))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No compatible constructor on NeoForge adapter " + target.entrypoint()));
            constructor.newInstance(modBus, container);
        } catch (InvocationTargetException failure) {
            throw rethrow("NeoForge adapter " + target.minecraft() + " failed to initialize", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize NeoForge adapter " + target.minecraft(), failure);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static ClientAdapterFactory<?, ?> newFactory() {
        NeoForgeAioTarget target = currentTarget();
        try {
            Class<?> factory = Class.forName(target.factory(), true,
                    Thread.currentThread().getContextClassLoader());
            return (ClientAdapterFactory) factory.getConstructor().newInstance();
        } catch (InvocationTargetException failure) {
            throw rethrow("NeoForge adapter factory " + target.minecraft() + " failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot create NeoForge adapter factory " + target.minecraft(), failure);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void ensureMixinCompatibility() {
        NeoForgeAioTarget target = currentTarget();
        try {
            Class<?> environment = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            Class<? extends Enum> levelType = (Class<? extends Enum>) Class.forName(
                    "org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel");
            Enum level = Enum.valueOf(levelType, "JAVA_" + target.javaRelease());
            Method setter = environment.getMethod("setCompatibilityLevel", levelType);
            setter.invoke(null, level);
        } catch (InvocationTargetException failure) {
            throw rethrow("Mixin cannot enable Java " + target.javaRelease()
                    + " for NeoForge adapter " + target.minecraft(), failure.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException failure) {
            throw new IllegalStateException("Mixin runtime does not support Java " + target.javaRelease()
                    + " required by NeoForge adapter " + target.minecraft(), failure);
        }
    }

    private static String versionValue(String methodName) {
        try {
            return versionValue(Class.forName("net.neoforged.fml.loading.FMLLoader"), methodName);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Cannot find the NeoForge loader", failure);
        }
    }

    static String versionValue(Class<?> loader, String methodName) {
        try {
            Object versionInfo;
            try {
                versionInfo = loader.getMethod("versionInfo").invoke(null);
            } catch (NoSuchMethodException legacyApiMissing) {
                Object current = loader.getMethod("getCurrent").invoke(null);
                versionInfo = loader.getMethod("getVersionInfo").invoke(current);
            }
            return String.valueOf(versionInfo.getClass().getMethod(methodName).invoke(versionInfo));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read NeoForge " + methodName, failure);
        }
    }

    private static IllegalStateException selectionFailure(
            String minecraftVersion, String neoForgeVersion, List<NeoForgeAioTarget> matches) {
        String runtime = neoForgeVersion == null
                ? "Minecraft " + minecraftVersion
                : "Minecraft " + minecraftVersion + " / NeoForge " + neoForgeVersion;
        return new IllegalStateException("Expected one NeoForge adapter for " + runtime + ", found "
                + matches.stream().map(NeoForgeAioTarget::minecraft).toList());
    }

    private static boolean contains(String range, String version) {
        if (range.startsWith("[") && range.endsWith("]") && !range.contains(",")) {
            return compareVersions(version, range.substring(1, range.length() - 1)) == 0;
        }
        if (range.length() < 3 || !(range.startsWith("[") || range.startsWith("("))
                || !(range.endsWith("]") || range.endsWith(")")) || !range.contains(",")) {
            throw new IllegalArgumentException("Unsupported version range " + range);
        }
        String[] bounds = range.substring(1, range.length() - 1).split(",", -1);
        int lower = bounds[0].isEmpty() ? 1 : compareVersions(version, bounds[0]);
        int upper = bounds[1].isEmpty() ? -1 : compareVersions(version, bounds[1]);
        boolean lowerMatches = bounds[0].isEmpty() || lower > 0 || (lower == 0 && range.startsWith("["));
        boolean upperMatches = bounds[1].isEmpty() || upper < 0 || (upper == 0 && range.endsWith("]"));
        return lowerMatches && upperMatches;
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = numericParts(left);
        int[] rightParts = numericParts(right);
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static int[] numericParts(String value) {
        String stable = value.split("-", 2)[0];
        return Arrays.stream(stable.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    private static RuntimeException rethrow(String message, Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException(message, failure);
    }
}
