package fun.prof_chen.teamviewer.buildtools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Exact-class relocator used to isolate NeoForge adapters in the All-in-One game layer. */
public final class AdapterRelocator {
    private static final String METADATA_PATH = "META-INF/teamviewer/neoforge-adapter.json";
    private static final String MOD_ANNOTATION = "Lnet/neoforged/fml/common/Mod;";

    private AdapterRelocator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                    "Usage: AdapterRelocator <input> <output> <class-prefix> <mixin-prefix> "
                            + "<module-name> <version> <mixin-classes>");
        }
        Set<String> mixinClasses = Set.of(args[6].split(","));
        relocate(Path.of(args[0]), Path.of(args[1]), normalizePrefix(args[2]),
                normalizePrefix(args[3]), args[4], args[5], mixinClasses);
    }

    static void relocate(Path input, Path output, String classPrefix, String mixinPrefix,
                         String moduleName, String version, Set<String> mixinClasses) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IOException("Raw adapter does not exist: " + input);
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (JarFile source = new JarFile(input.toFile())) {
            List<JarEntry> classEntries = source.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            if (classEntries.isEmpty()) {
                throw new IOException("Raw adapter contains no classes: " + input);
            }
            Set<String> classNames = classEntries.stream()
                    .map(entry -> entry.getName().substring(0,
                            entry.getName().length() - ".class".length()))
                    .collect(java.util.stream.Collectors.toSet());
            for (String mixinClass : mixinClasses) {
                if (!classNames.contains(mixinClass)) {
                    throw new IOException("Raw adapter is missing declared Mixin " + mixinClass + ": " + input);
                }
            }
            JarEntry metadata = source.getJarEntry(METADATA_PATH);
            if (metadata == null) {
                throw new IOException("Raw adapter is missing " + METADATA_PATH + ": " + input);
            }

            Map<String, String> relocations = new HashMap<>();
            for (JarEntry entry : classEntries) {
                String internalName = entry.getName().substring(0, entry.getName().length() - ".class".length());
                boolean isMixin = mixinClasses.stream().anyMatch(
                        mixin -> internalName.equals(mixin) || internalName.startsWith(mixin + "$"));
                relocations.put(internalName, (isMixin ? mixinPrefix : classPrefix) + "/" + internalName);
            }
            SimpleRemapper remapper = new SimpleRemapper(Opcodes.ASM9, relocations);

            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue("Automatic-Module-Name", moduleName);
            attributes.putValue("FMLModType", "GAMELIBRARY");
            attributes.putValue("Implementation-Version", version);

            Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
            try (OutputStream fileOutput = Files.newOutputStream(temporary);
                 JarOutputStream target = new JarOutputStream(fileOutput, manifest)) {
                writeEntry(target, METADATA_PATH, readAll(source, metadata));
                for (JarEntry entry : classEntries) {
                    byte[] original = readAll(source, entry);
                    ClassReader reader = new ClassReader(original);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor withoutModEntrypoint = new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitAnnotation(
                                String descriptor, boolean visible) {
                            if (MOD_ANNOTATION.equals(descriptor)) return null;
                            return super.visitAnnotation(descriptor, visible);
                        }
                    };
                    reader.accept(new ClassRemapper(withoutModEntrypoint, remapper), 0);
                    String relocatedName = relocations.get(reader.getClassName()) + ".class";
                    writeEntry(target, relocatedName, writer.toByteArray());
                }
            } catch (Throwable failure) {
                Files.deleteIfExists(temporary);
                throw failure;
            }
            Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix.replace('.', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isBlank()) throw new IllegalArgumentException("Relocation prefix must not be empty");
        return normalized;
    }

    private static byte[] readAll(JarFile source, JarEntry entry) throws IOException {
        try (InputStream input = source.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] content) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(content);
        output.closeEntry();
    }
}
