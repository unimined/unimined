package xyz.wagyourtail.unimined.jarmodagent;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;

public class JarModAgent {

    private final String[] transformers;
    private final URLClassLoader priorityClasspath;
    private final Instrumentation instrumentation;
    private final TransformerManager transformerManager;

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("[Unimined.JarModAgent] Starting agent with args: " + agentArgs);
        // process args
        ArgsReader argsReader = new ArgsReader(agentArgs);
        Map<String, String> args = argsReader.readAll();
        System.out.println("[Unimined.JarModAgent] Agent args:");
        for (Map.Entry<String, String> entry : args.entrySet()) {
            System.out.println("[Unimined.JarModAgent]     " + entry.getKey() + " = " + entry.getValue());
        }
        JarModAgent agent = new JarModAgent(instrumentation, args);
        agent.init();
        System.out.println("[Unimined.JarModAgent] Agent started");
    }

    public JarModAgent(Instrumentation instrumentation, Map<String, String> args) {
        this.instrumentation = instrumentation;
        transformers = Optional.ofNullable(args.get("transforms"))
            .map(it -> it.split(File.pathSeparator))
            .orElse(new String[0]);
        priorityClasspath = new URLClassLoader(Optional.ofNullable(args.get("priorityClasspath"))
            .map(it -> Arrays.stream(
                it.split(File.pathSeparator)).map(e -> {
                try {
                    return new File(e).toURI().toURL();
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            }).toArray(URL[]::new))
            .orElse(new URL[0]));

        transformerManager = new TransformerManager(new BasicClassProvider());
    }

    public void init() {
        try {
            registerTransforms();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        transformerManager.hookInstrumentation(instrumentation);
    }


    public void registerTransforms() throws IOException {
        System.out.println("[Unimined.JarModAgent] Loading transforms...");
        List<String> transformers = new ArrayList<>();
        for (String file : this.transformers) {
            try (InputStream is = JarModAgent.class.getClassLoader().getResourceAsStream(file)) {
                if (is == null) {
                    throw new IOException("Could not find transform file: " + file);
                }
                System.out.println("[Unimined.JarModAgent] Loading transforms: " + file);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        transformers.add(line);
                    }
                }
            }
        }
        int fail = 0;
        for (String transformer : transformers) {
            try {
                transformerManager.addTransformer(transformer);
            } catch (Exception e) {
                System.out.println("[Unimined.JarModAgent] Failed to load transform: " + transformer);
                e.printStackTrace();
                fail++;
            }
        }
        if (fail > 0) {
            throw new RuntimeException("Failed to load " + fail + " transforms");
        }
        System.out.println("[Unimined.JarModAgent] Loaded " + transformers.size() + " transforms from " + this.transformers.length + " files");
    }

    private byte[] readAllBytes(InputStream is) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = is.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

    public void priorityClasspathFix() {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                try (InputStream is = priorityClasspath.getResourceAsStream(className.replace(".", "/") + ".class")) {
                    if (is != null) {
                        System.out.println("[Unimined.JarModAgent] Found class: \"" + className + "\" in priority classpath");
                        return readAllBytes(is);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

}
