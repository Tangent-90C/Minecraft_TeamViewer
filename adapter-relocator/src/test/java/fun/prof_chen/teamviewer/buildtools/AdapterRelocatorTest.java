package fun.prof_chen.teamviewer.buildtools;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdapterRelocatorTest {
    @Test
    void relocatesOnlyClassesPresentInTheAdapter() throws Exception {
        Path directory = Files.createTempDirectory("adapter-relocator-test");
        Path input = directory.resolve("input.jar");
        Path output = directory.resolve("output.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(input), manifest)) {
            write(jar, "example/Adapter.class", adapterClass());
            write(jar, "example/Helper.class", helperClass());
            write(jar, "example/PacketMixin.class", mixinClass());
            write(jar, "META-INF/teamviewer/neoforge-adapter.json", "{}".getBytes());
        }

        AdapterRelocator.relocate(input, output, "internal/adapter/mc_1_20_2",
                "internal/mixin/mc_1_20_2", "internal.mc_1_20_2", "1.0",
                Set.of("example/PacketMixin"));

        try (JarFile jar = new JarFile(output.toFile())) {
            JarEntry relocated = jar.getJarEntry("internal/adapter/mc_1_20_2/example/Adapter.class");
            assertNotNull(relocated);
            assertNotNull(jar.getJarEntry("internal/mixin/mc_1_20_2/example/PacketMixin.class"));
            assertNull(jar.getJarEntry("example/Adapter.class"));
            assertEquals("GAMELIBRARY", jar.getManifest().getMainAttributes().getValue("FMLModType"));
            ClassReader reader = new ClassReader(jar.getInputStream(relocated));
            assertEquals("internal/adapter/mc_1_20_2/example/Adapter", reader.getClassName());
            String[] fieldDescriptor = new String[1];
            int[] modAnnotations = new int[1];
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if ("Lnet/neoforged/fml/common/Mod;".equals(descriptor)) modAnnotations[0]++;
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    if ("helper".equals(name)) fieldDescriptor[0] = descriptor;
                    return null;
                }
            }, 0);
            assertEquals("Linternal/adapter/mc_1_20_2/example/Helper;", fieldDescriptor[0]);
            assertEquals(0, modAnnotations[0]);
        }
    }

    private static byte[] adapterClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Adapter", null, "java/lang/Object", null);
        writer.visitAnnotation("Lnet/neoforged/fml/common/Mod;", true).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "helper", "Lexample/Helper;", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] helperClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Helper", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] mixinClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/PacketMixin", null,
                "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void write(JarOutputStream jar, String name, byte[] content) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }
}
