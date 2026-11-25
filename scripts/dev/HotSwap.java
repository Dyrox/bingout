import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hot-swaps changed classes into the running development client, through its debug port (see scripts/dev/hotswap.sh).
 *
 * <pre>
 * java HotSwap.java snapshot &lt;hashes file&gt; &lt;class dir&gt;...          (right after the client started)
 * java HotSwap.java swap &lt;debug port&gt; &lt;hashes file&gt; &lt;class dir&gt;...
 * </pre>
 *
 * A class dir prefixed with "jar:" is one the client doesn't load from (it loads that module's jar, which isn't rebuilt
 * while it runs): a changed class from there that isn't loaded yet would load the old version later, so that needs a
 * restart too.
 *
 * "snapshot" records the class files the client started with. "swap" redefines the classes whose class files changed
 * since (by content: recompiling a class to the same bytes isn't a change) in the client's JVM, like a debugger's
 * HotSwap: all of them, or none if the JVM refuses one (JVMTI RedefineClasses is all or nothing). The JVM only accepts
 * new method bodies; for anything else (even a new lambda: a new synthetic method), this lists what changed
 * structurally and exits with 3: the client has to be restarted. Swapped classes are recorded as the new state.
 */
public class HotSwap {
    private record Member(String name, String descriptor) implements Comparable<Member> {
        public int compareTo(Member o) { return toString().compareTo(o.toString()); }
        public String toString() { return name + " " + descriptor; }
    }

    private record Shape(String superName, Set<String> interfaces, Set<Member> fields, Set<Member> methods) {}

    public static void main(String[] args) throws Exception {
        if (args[0].equals("snapshot")) {
            Map<String, String> hashes = new TreeMap<>();
            classFiles(Arrays.copyOfRange(args, 2, args.length), null).forEach((name, file) -> hashes.put(name, hash(file)));
            writeHashes(Path.of(args[1]), hashes);
            return;
        }

        int port = Integer.parseInt(args[1]);
        Path hashesFile = Path.of(args[2]);
        Map<String, String> hashes = readHashes(hashesFile);
        // class name -> new class file
        Map<String, Path> changed = new TreeMap<>();
        Map<String, String> newHashes = new TreeMap<>();
        Set<String> fromJars = new TreeSet<>();
        classFiles(Arrays.copyOfRange(args, 3, args.length), fromJars).forEach((name, file) -> {
            String hash = hash(file);
            if (!hash.equals(hashes.get(name))) {
                changed.put(name, file);
                newHashes.put(name, hash);
            }
        });
        if (changed.isEmpty()) {
            System.out.println("No changed classes");
            return;
        }

        VirtualMachine vm = attach(port);
        int exitCode = 0;
        try {
            Map<ReferenceType, byte[]> redefine = new LinkedHashMap<>();
            List<String> notLoaded = new ArrayList<>();
            List<String> mixins = new ArrayList<>();
            for (var entry : changed.entrySet()) {
                // mixin classes are merged into Minecraft's classes when those load: they're never loaded themselves
                if (entry.getKey().contains(".mixin.")) {
                    mixins.add(entry.getKey());
                    continue;
                }
                List<ReferenceType> types = vm.classesByName(entry.getKey());
                if (types.isEmpty()) notLoaded.add(entry.getKey());
                byte[] bytes = Files.readAllBytes(entry.getValue());
                for (ReferenceType type : types) redefine.put(type, bytes);
            }

            if (!redefine.isEmpty()) {
                try {
                    vm.redefineClasses(redefine);
                    for (ReferenceType type : redefine.keySet()) {
                        System.out.println("Swapped " + type.name());
                        hashes.put(type.name(), newHashes.get(type.name()));
                    }
                    writeHashes(hashesFile, hashes);
                } catch (Throwable e) {
                    System.out.println("The JVM refused the new classes (" + e + "); nothing was swapped.");
                    for (var entry : redefine.entrySet()) {
                        List<String> changes = structuralChanges(shapeOf(entry.getKey()), parse(entry.getValue()));
                        if (!changes.isEmpty()) {
                            System.out.println("  " + entry.getKey().name() + ": " + String.join(", ", changes));
                        }
                    }
                    exitCode = 3;
                }
            }
            for (String name : notLoaded) {
                if (fromJars.contains(name)) {
                    System.out.println("Not loaded yet: " + name + " (it would load the old version from its module's jar)");
                    exitCode = 3;
                } else {
                    hashes.put(name, newHashes.get(name));
                }
            }
            if (exitCode == 0) writeHashes(hashesFile, hashes);
            if (!mixins.isEmpty()) {
                System.out.println("Mixins changed (" + String.join(", ", mixins) + "): they only apply after a restart.");
                exitCode = 3;
            }
        } finally {
            vm.dispose();
        }
        System.exit(exitCode);
    }

    /** Class name -> class file, for the class files in these directories (adding those of "jar:" dirs to fromJars) */
    private static Map<String, Path> classFiles(String[] dirNames, Set<String> fromJars) throws IOException {
        Map<String, Path> classes = new TreeMap<>();
        for (String dirName : dirNames) {
            boolean jar = dirName.startsWith("jar:");
            Path dir = Path.of(dirName.substring(jar ? 4 : 0));
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String name = dir.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", "");
                    classes.put(name, file);
                    if (jar && fromJars != null) fromJars.add(name);
                }
            }
        }
        return classes;
    }

    private static String hash(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> readHashes(Path file) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        for (String line : Files.readAllLines(file)) {
            int space = line.indexOf(' ');
            if (space > 0) hashes.put(line.substring(space + 1), line.substring(0, space));
        }
        return hashes;
    }

    private static void writeHashes(Path file, Map<String, String> hashes) throws IOException {
        List<String> lines = new ArrayList<>();
        hashes.forEach((name, hash) -> lines.add(hash + " " + name));
        Files.write(file, lines);
    }

    private static VirtualMachine attach(int port) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.transport().name().equals("dt_socket"))
            .findFirst()
            .orElseThrow();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(String.valueOf(port));
        return connector.attach(arguments);
    }

    /** What the JVM's HotSwap can't redefine: other supertypes, added/removed fields or (non-private) methods */
    private static List<String> structuralChanges(Shape old, Shape now) {
        List<String> changes = new ArrayList<>();
        if (!String.valueOf(old.superName()).equals(String.valueOf(now.superName()))) {
            changes.add("superclass " + old.superName() + " -> " + now.superName());
        }
        if (!old.interfaces().equals(now.interfaces())) changes.add("interfaces " + old.interfaces() + " -> " + now.interfaces());
        diff("field", old.fields(), now.fields(), changes);
        diff("method", old.methods(), now.methods(), changes);
        return changes;
    }

    private static void diff(String kind, Set<Member> old, Set<Member> now, List<String> changes) {
        for (Member m : now) if (!old.contains(m)) changes.add("added " + kind + " " + m);
        for (Member m : old) if (!now.contains(m)) changes.add("removed " + kind + " " + m);
    }

    private static Shape shapeOf(ReferenceType type) {
        String superName = type instanceof ClassType c
            ? (c.superclass() == null ? null : c.superclass().name())
            : "java.lang.Object";
        List<? extends ReferenceType> interfaces = type instanceof ClassType c ? c.interfaces()
            : type instanceof InterfaceType i ? i.superinterfaces() : List.of();
        return new Shape(
            superName,
            interfaces.stream().map(ReferenceType::name).collect(Collectors.toCollection(TreeSet::new)),
            type.fields().stream().map(f -> new Member(f.name(), f.signature())).collect(Collectors.toCollection(TreeSet::new)),
            type.methods().stream().filter(m -> !m.name().equals("<clinit>"))
                .map(m -> new Member(m.name(), m.signature())).collect(Collectors.toCollection(TreeSet::new))
        );
    }

    /** The supertypes, fields and methods of a class file */
    private static Shape parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNames = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 7 -> classNames[i] = in.readUnsignedShort();
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++;
                }
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                default -> throw new IOException("Unknown constant pool tag " + tag);
            }
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int superIndex = in.readUnsignedShort();
        String superName = superIndex == 0 ? null : utf8[classNames[superIndex]].replace('/', '.');
        Set<String> interfaces = new TreeSet<>();
        for (int i = in.readUnsignedShort(); i > 0; i--) interfaces.add(utf8[classNames[in.readUnsignedShort()]].replace('/', '.'));
        Set<Member> fields = members(in, utf8);
        Set<Member> methods = members(in, utf8);
        methods.removeIf(m -> m.name().equals("<clinit>"));
        return new Shape(superName, interfaces, fields, methods);
    }

    private static Set<Member> members(DataInputStream in, String[] utf8) throws IOException {
        Set<Member> members = new TreeSet<>();
        for (int n = in.readUnsignedShort(); n > 0; n--) {
            in.readUnsignedShort();
            String name = utf8[in.readUnsignedShort()];
            String descriptor = utf8[in.readUnsignedShort()];
            for (int a = in.readUnsignedShort(); a > 0; a--) {
                in.readUnsignedShort();
                in.skipNBytes(in.readInt());
            }
            members.add(new Member(name, descriptor));
        }
        return members;
    }
}
