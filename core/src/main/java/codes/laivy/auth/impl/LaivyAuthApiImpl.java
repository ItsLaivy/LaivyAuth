package codes.laivy.auth.impl;

import codes.laivy.auth.LaivyAuth;
import codes.laivy.auth.api.LaivyAuthApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarFile;

final class LaivyAuthApiImpl implements LaivyAuthApi {

    // Object

    private static final @NotNull ReentrantLock lock = new ReentrantLock();
    private static final @NotNull Set<Mapping> mappings = new HashSet<>();

    private LaivyAuthApiImpl(@NotNull LaivyAuth plugin) {
        // Load all mappings
        @NotNull File file = new File(plugin.getDataFolder(), "/mappings/");

        @NotNull File @Nullable [] mappingFiles = file.listFiles();
        if (mappingFiles != null) for (@NotNull File mappingFile : mappingFiles) try {
            if (!mappingFile.isFile() || !mappingFile.getName().toLowerCase().endsWith(".jar")) {
                continue;
            }

            try (@NotNull JarFile jar = new JarFile(mappingFile)) {
                @NotNull URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{ mappingFile.toURI().toURL() }, LaivyAuth.class.getClassLoader());
                @NotNull Class<?> main = classLoader.loadClass(jar.getManifest().getMainAttributes().getValue("Main-Class"));

                if (Mapping.class.isAssignableFrom(main)) {
                    //noinspection unchecked
                    @NotNull Constructor<Mapping> constructor = ((Class<Mapping>) main).getDeclaredConstructor(ClassLoader.class);
                    constructor.setAccessible(true);

                    @NotNull Mapping mapping = constructor.newInstance(classLoader);
                    mappings.add(mapping);
                } else {
                    throw new RuntimeException("The main class of mapping '" + mappingFile.getName() + "' isn't an instance of '" + Mapping.class.getName() + "'");
                }
            } catch (@NotNull InvocationTargetException | @NotNull InstantiationException | @NotNull IllegalAccessException e) {
                throw new RuntimeException("cannot instantiate main class of mapping '" + mappingFile.getName() + "'", e);
            }
        } catch (@NotNull NoSuchMethodException e) {
            throw new RuntimeException("cannot find a valid constructor of mapping '" + mappingFile.getName() + "'", e);
        } catch (@NotNull ClassNotFoundException e) {
            throw new RuntimeException("cannot find main class of mapping '" + mappingFile.getName() + "'", e);
        } catch (@NotNull IOException e) {
            throw new RuntimeException("an unknown error occurred trying to load mapping '" + mappingFile.getName() + "'", e);
        }

        System.out.println("Loaded '" + mappings.size() + "' mappings");
    }

    // Getters

    @Override
    public boolean isRegistered(@NotNull UUID uuid) {
        return false;
    }
    @Override
    public boolean isAuthenticated(@NotNull UUID uuid) {
        return false;
    }

    // Loaders

    @Override
    public void flush() throws IOException {

    }

}