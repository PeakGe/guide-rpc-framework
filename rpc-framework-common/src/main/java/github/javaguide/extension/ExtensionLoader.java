package github.javaguide.extension;

import github.javaguide.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * refer to dubbo spi: https://dubbo.apache.org/zh-cn/docs/source_code_guide/dubbo-spi.html
 */
@Slf4j
public final class ExtensionLoader<T> {

    //类信息
    private static final String SERVICE_DIRECTORY = "META-INF/extensions/";//需要加载的类们资源文件的目录
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();//接口的Class对象-类加载器Map
    private static final Map<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();//接口实现类Class对象-类实例Map

    //对象信息（一个type对应一个ExtensionLoader对象）
    private final Class<?> type;//需要加载的接口全类名（用于确定要加载的类们所在资源文件的文件名（在META-INF/extensions/目录下））
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();//缓存已加载的扩展名对应实现类的实例<扩展名-扩展名对应实现类的实例>
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();//缓存已加载的Class对象<扩展名-实现类Class对象>

    private ExtensionLoader(Class<?> type) {
        this.type = type;
    }

    /**
     * 获取指定接口全类名的扩展名加载器实例，并加入EXTENSION_LOADERS中
     * @param type 需要加载的接口全类名(必须用SPI注解)
     * @return: github.javaguide.extension.ExtensionLoader<S>
     * @author: gefeng
     * @date: 2023/2/7 15:04
     */
    public static <S> ExtensionLoader<S> getExtensionLoader(Class<S> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type should not be null.");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface.");
        }
        if (type.getAnnotation(SPI.class) == null) {
            throw new IllegalArgumentException("Extension type must be annotated by @SPI");
        }
        // firstly get from cache, if not hit, create one
        ExtensionLoader<S> extensionLoader = (ExtensionLoader<S>) EXTENSION_LOADERS.get(type);
        if (extensionLoader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<S>(type));
            extensionLoader = (ExtensionLoader<S>) EXTENSION_LOADERS.get(type);
        }
        return extensionLoader;
    }

    /**
     * 获取ExtensionLoader指定扩展名的接口实现类实例，并封装为Holder类型放入cachedInstances中
     * 1.尝试从cachedInstances中获取指定扩展名对应实现类的实例
     * 2.获取失败，则createExtension(name)创建（双重检查加锁）
     * @param name 接口实现类扩展名
     * @return: T
     * @author: gefeng
     * @date: 2023/2/7 15:08
     */
    public T getExtension(String name) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("Extension name should not be null or empty.");
        }
        // firstly get from cache, if not hit, create one
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        // create a singleton if no instance exists
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 获取指定扩展名对应实现类的实例
     * 1.使用ExtensionLoader类的加载器加载type指定的文件中所有列出的类，后放入<扩展名-实现类Class对象>cachedClasses缓存中，得到该map
     * 2.从该map中获取指定扩展名对应的实现类Class对象
     * 3.尝试从EXTENSION_INSTANCES获取该实现类Class对象的实例
     * 4.获取失败，则clazz.newInstance()创建实例，并放入EXTENSION_INSTANCES
     * @param name 需要创建实现类实例的扩展名
     * @return: T
     * @author: gefeng
     * @date: 2023/2/7 15:23
     */
    private T createExtension(String name) {
        // load all extension classes of type T from file and get specific one by name
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new RuntimeException("No such extension of name " + name);
        }
        T instance = (T) EXTENSION_INSTANCES.get(clazz);
        if (instance == null) {
            try {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return instance;
    }

    /**
     * 使用ExtensionLoader类的加载器加载type指定的文件中所有列出的类，后放入<扩展名-实现类Class对象>cachedClasses缓存中，并返回该map
     * @param
     * @return: java.util.Map<java.lang.String,java.lang.Class<?>>
     * @author: gefeng
     * @date: 2022/8/30 19:07
     */
    private Map<String, Class<?>> getExtensionClasses() {
        // get the loaded extension class from the cache
        Map<String, Class<?>> classes = cachedClasses.get();
        // double check-双重检查
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = new HashMap<>();
                    // load all extensions from our extensions directory
                    loadDirectory(classes);
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * 1.根据type接口全类名获取该type对应的资源文件名
     * 2.获取ExtensionLoader的类加载器
     * 3.使用ExtensionLoader类的加载器加载type指定的文件中所有列出扩展名的实现类
     * 使用ExtensionLoader类的加载器加载type指定的文件中所有列出的实现类，后放入<扩展名-实现类Class对象>extensionClasses中
     * @param extensionClasses 1
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 18:57
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses) {
        String fileName = ExtensionLoader.SERVICE_DIRECTORY + type.getName();
        try {
            Enumeration<URL> urls;
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL resourceUrl = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceUrl);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 使用指定类加载器（classLoader）加载指定资源（resourceUrl）中的列出的所有类
     * 资源中格式例（扩展名-全类名）：zk=github.javaguide.registry.zk.ZkServiceRegistryImpl
     * @param extensionClasses 1
     * @param classLoader 2
     * @param resourceUrl 3
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 18:44
     */
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, URL resourceUrl) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), UTF_8))) {
            String line;
            // read every line
            while ((line = reader.readLine()) != null) {
                // get index of comment
                final int ci = line.indexOf('#');
                if (ci >= 0) {
                    // string after # is comment so we ignore it
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        final int ei = line.indexOf('=');
                        String name = line.substring(0, ei).trim();
                        String clazzName = line.substring(ei + 1).trim();
                        // our SPI use key-value pair so both of them must not be empty
                        if (name.length() > 0 && clazzName.length() > 0) {
                            Class<?> clazz = classLoader.loadClass(clazzName);
                            extensionClasses.put(name, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        log.error(e.getMessage());
                    }
                }

            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
