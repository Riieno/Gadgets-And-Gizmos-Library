package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

// Prefer an installed LuaJ without publishing fallback packages or sharing interpreter statics
public final class LuaJRuntimes{
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaJRuntimes.class);
    private static final String ENGINE = "com.rieno.gadgetsandgizmos.lib.script.LuaJEngine";
    private static final String GLOBALS = "org/luaj/vm2/Globals.class";
    private static final String BUNDLED_VERSION = "3.0.1";
    private static final String BUNDLED = "runtime/luaj-runtime.jar.bin";
    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+");
    private static final Pattern LUA_JAR = Pattern.compile("luaj-(?:core|jse)-([0-9][a-zA-Z0-9.+_-]*)\\.jar");
    public record RuntimeInfo(String version, String source, boolean bundled){}
    private LuaJRuntimes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static RuntimeInfo current(){ return Selected.RUNTIME.info; }
    public static ScriptSession create(UUID id, ScriptHost host){ return Selected.RUNTIME.create(id, host); }

    // Keep selection lazy so other mods finish installing their library resources first
    private static final class Selected{
        private static final Runtime RUNTIME = select(LuaJRuntimes.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
    }
    static Runtime select(ClassLoader... loaders){
        Map<String, Source> sources = new LinkedHashMap<>();
        for(var loader : loaders){
            if(loader == null) continue;
            try{
                for(URL url : resources(loader, GLOBALS)){
                    var source = Source.installed(url, GLOBALS, java.util.Arrays.stream(loaders).filter(Objects::nonNull).toList()); sources.putIfAbsent(source.info.source(), source);
                }
            }catch(IOException | SecurityException ex){ LOGGER.debug("Cannot inspect a LuaJ provider", ex); }
        }
        var candidates = new ArrayList<>(sources.values());
        candidates.sort((a, b) -> compare(b.info.version(), a.info.version()));
        for(var source : candidates){
            if(!source.info.version().equals("unknown") && compare(source.info.version(), BUNDLED_VERSION) < 0) continue;
            try{
                var runtime = new Runtime(source);
                LOGGER.info("Using installed LuaJ {} from {} in a private sandbox loader", source.info.version(), source.info.source());
                return runtime;
            }catch(ReflectiveOperationException | LinkageError | RuntimeException ex){
                LOGGER.warn("LuaJ provider {} is incompatible with the script sandbox; trying another runtime", source.info.source(), ex);
            }
        }
        try{
            var runtime = new Runtime(Source.bundled());
            LOGGER.info("Using private bundled LuaJ {}", BUNDLED_VERSION); return runtime;
        }catch(IOException | ReflectiveOperationException | LinkageError ex){ throw new IllegalStateException("Cannot load the private LuaJ runtime", ex); }
    }

    static final class Runtime{
        private final RuntimeInfo info;
        private final Constructor<? extends ScriptSession> constructor;
        private Runtime(Source source) throws ReflectiveOperationException{
            info = source.info;
            var loader = new Loader(source);
            var engine = Class.forName(ENGINE, true, loader).asSubclass(ScriptSession.class);
            engine.getMethod("verifyRuntime").invoke(null);
            constructor = engine.getConstructor(UUID.class, ScriptHost.class);
        }
        RuntimeInfo info(){ return info; }
        ScriptSession create(UUID id, ScriptHost host){
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(host, "host");
            try{ return constructor.newInstance(id, host); }
            catch(ReflectiveOperationException ex){
                Throwable cause = ex instanceof InvocationTargetException invocation ? invocation.getCause() : ex;
                throw new IllegalStateException("Cannot create the LuaJ script session", cause);
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Compare interpreter versions independently of the carrier mod's own version
    private static int compare(String first, String second){
        var a = parts(first); var b = parts(second);
        for(int idx = 0; idx < Math.max(a.size(), b.size()); idx++){
            int res = (idx < a.size() ? a.get(idx) : BigInteger.ZERO).compareTo(idx < b.size() ? b.get(idx) : BigInteger.ZERO);
            if(res != 0) return res;
        }
        return 0;
    }
    private static InputStream open(URL url) throws IOException{
        var connection = url.openConnection(); connection.setUseCaches(false); return connection.getInputStream();
    }
    private static List<URL> resources(ClassLoader loader, String path) throws IOException{
        var urls = new ArrayList<>(Collections.list(loader.getResources(path)));
        // Named module loaders can resolve a class without exposing it through resource enumeration
        try{
            var type = Class.forName(path.substring(0, path.length() - 6).replace('/', '.'), false, loader);
            URL url = type.getResource('/' + path);
            if(url != null && !urls.contains(url)) urls.add(url);
        }catch(ClassNotFoundException | LinkageError | SecurityException ignored){}
        return urls;
    }
    private static List<BigInteger> parts(String version){
        var match = VERSION.matcher(version); if(!match.find()) return List.of();
        return java.util.Arrays.stream(match.group().split("\\.")).map(BigInteger::new).toList();
    }

    private static final class Source{
        private final RuntimeInfo info;
        private final URL root;
        private final Map<String, byte[]> classes;
        private final List<ClassLoader> loaders;
        private final List<Source> companions = new ArrayList<>();
        private Source(RuntimeInfo info, URL root, Map<String, byte[]> classes, List<ClassLoader> loaders){
            this.info = info; this.root = root; this.classes = classes; this.loaders = loaders;
        }
        private static Source installed(URL resource, String path, List<ClassLoader> loaders) throws IOException{
            URL root = new URL(resource, "../".repeat((int)path.chars().filter(ch -> ch == '/').count()));
            String version = "unknown";
            for(String artifact : List.of("luaj-core", "luaj-jse")){
                try(var stream = open(new URL(root, "META-INF/maven/org.luaj/" + artifact + "/pom.properties"))){
                    var props = new Properties(); props.load(stream); String val = props.getProperty("version", "unknown");
                    if(compare(val, version) > 0) version = val;
                }catch(IOException ignored){}
            }
            try(var stream = open(new URL(root, "META-INF/MANIFEST.MF"))){
                var attrs = new Manifest(stream).getMainAttributes();
                String paths = Objects.toString(attrs.getValue("Class-Path"), "") + " " + root;
                var match = LUA_JAR.matcher(paths);
                while(match.find()) if(compare(match.group(1), version) > 0) version = match.group(1);
                String title = Objects.toString(attrs.getValue("Implementation-Title"), "").toLowerCase(java.util.Locale.ROOT);
                String val = attrs.getValue("Implementation-Version");
                if(title.contains("luaj") && val != null && compare(val, version) > 0) version = val;
            }catch(IOException ignored){}
            if(version.equals("unknown")){
                var match = LUA_JAR.matcher(root.toExternalForm()); if(match.find()) version = match.group(1);
            }
            return new Source(new RuntimeInfo(version, root.toExternalForm(), false), root, Map.of(), loaders);
        }
        private static Source bundled() throws IOException{
            var classes = new LinkedHashMap<String, byte[]>();
            try(InputStream resource = LuaJRuntimes.class.getResourceAsStream(BUNDLED)){
                if(resource == null) throw new IOException("Missing private LuaJ runtime");
                try(var jar = new JarInputStream(resource)){
                    for(var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()){
                        if(entry.getName().startsWith("org/luaj/") && entry.getName().endsWith(".class")) classes.put(entry.getName(), jar.readAllBytes());
                    }
                }
            }
            if(!classes.containsKey(GLOBALS)) throw new IOException("The private LuaJ runtime is incomplete");
            return new Source(new RuntimeInfo(BUNDLED_VERSION, BUNDLED, true), null, Map.copyOf(classes), List.of());
        }
        private byte[] read(String path) throws IOException{
            if(root == null){ var bytes = classes.get(path); if(bytes == null) throw new IOException("Missing private LuaJ class " + path); return bytes; }
            IOException missing;
            try{ return direct(path); }catch(IOException ex){ missing = ex; }
            for(var companion : companions) try{ return companion.direct(path); }catch(IOException ignored){}
            // Core and JSE can be installed separately, but must come from the same interpreter version
            if(!info.version().equals("unknown")) for(var loader : loaders){
                if(loader == null) continue;
                for(var resource : resources(loader, path)){
                    var companion = installed(resource, path, List.of());
                    if(companion.info.source().equals(info.source()) || companion.info.version().equals("unknown")
                            || compare(companion.info.version(), info.version()) != 0) continue;
                    if(companions.stream().noneMatch(val -> val.info.source().equals(companion.info.source()))) companions.add(companion);
                    try{ return companion.direct(path); }catch(IOException ignored){}
                }
            }
            throw missing;
        }
        private byte[] direct(String path) throws IOException{
            try(var stream = open(new URL(root, path))){ return stream.readAllBytes(); }
        }
    }

    // Only the sandbox implementation and its selected LuaJ classes cross this private boundary
    private static final class Loader extends ClassLoader{
        private final Source source;
        private Loader(Source source){ super(LuaJRuntimes.class.getClassLoader()); this.source = source; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
            if(!name.startsWith("org.luaj.") && !name.equals(ENGINE) && !name.startsWith(ENGINE + "$")) return super.loadClass(name, resolve);
            synchronized(getClassLoadingLock(name)){
                Class<?> type = findLoadedClass(name);
                if(type == null){
                    String path = name.replace('.', '/') + ".class";
                    try{
                        byte[] bytes;
                        if(name.startsWith("org.luaj.")) bytes = source.read(path);
                        else try(var stream = LuaJRuntimes.class.getResourceAsStream("/" + path)){
                            if(stream == null) throw new IOException("Missing sandbox class " + path); bytes = stream.readAllBytes();
                        }
                        type = defineClass(name, bytes, 0, bytes.length);
                    }catch(IOException ex){ throw new ClassNotFoundException(name, ex); }
                }
                if(resolve) resolveClass(type); return type;
            }
        }
        @Override public URL getResource(String name){
            if(!name.startsWith("org/luaj/")) return super.getResource(name);
            if(source.root == null) return null;
            try{ return new URL(source.root, name); }catch(java.net.MalformedURLException ex){ return null; }
        }
    }
}
