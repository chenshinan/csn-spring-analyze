# springboot项目启动源码分析

```java
@SpringBootApplication
public class CsnSpringAnalyzeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsnSpringAnalyzeApplication.class, args);
    }
}
```

这是一个非常普通的java程序入口，一个符合约定的静态main方法。在这个main方法中，调用了SpringApplication的静态run方法，并将Application类对象和main方法的参数args作为参数传递了进去

## SpirngApplication的静态run方法

```java
public static ConfigurableApplicationContext run(Class<?> primarySource,String... args) {
    return run(new Class<?>[] { primarySource }, args);
}

public static ConfigurableApplicationContext run(Class<?>[] primarySources,String[] args) {
    return new SpringApplication(primarySources).run(args);
}
```

## 构造SpringApplication对象

```java
public SpringApplication(Class<?>... primarySources) {
    this(null, primarySources);
}
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    this.resourceLoader = resourceLoader;
    Assert.notNull(primarySources, "PrimarySources must not be null");
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
    //通过classpath推断出当前启动的应用类型
    this.webApplicationType = WebApplicationType.deduceFromClasspath();
    //设置成员变量initializers，获取用于初始化ApplicationContext的初始化实例对象
    setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
    //设置成员变量listeners，获取用于监听Application的监听实例对象
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
    //推断main所在的类
    this.mainApplicationClass = deduceMainApplicationClass();
}
```

### 判断WebApplicationType

```java
private static final String[] SERVLET_INDICATOR_CLASSES = { "javax.servlet.Servlet","org.springframework.web.context.ConfigurableWebApplicationContext" };
private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";
private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";
private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";
static WebApplicationType deduceFromClasspath() {
    if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null)
        && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
        && !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS, null)) {
        return WebApplicationType.REACTIVE;
    }
    for (String className : SERVLET_INDICATOR_CLASSES) {
        if (!ClassUtils.isPresent(className, null)) {
            return WebApplicationType.NONE;
        }
    }
    return WebApplicationType.SERVLET;
}
```

通过`ClassUtils.isPresent(url,null)`来判断项目中是否包含对应的类，此处就是判断是否存在Web应用程序对应的包类，反之则不然

### 设置成员变量initializers

```java
private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
    return getSpringFactoriesInstances(type, new Class<?>[] {});
}
private <T> Collection<T> getSpringFactoriesInstances(Class<T> type,Class<?>[] parameterTypes, Object... args) {
    //通过ClassUtils获取类加载器
    ClassLoader classLoader = getClassLoader();
    //通过SpringFactoriesLoader获取配置中某个类的实现类
    Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
    //通过反射创建出所有实现类对象实例
    List<T> instances = createSpringFactoriesInstances(type, parameterTypes,classLoader, args, names);
    //对实例进行排序
    AnnotationAwareOrderComparator.sort(instances);
    return instances;
}
```

initializers成员变量，是一个ApplicationContextInitializer类型对象的集合。 顾名思义，ApplicationContextInitializer是一个可以用来初始化ApplicationContext的接口，通过`getSpringFactoriesInstances方法`加载出实例

#### getClassLoader

通过`ClassUtils.getDefaultClassLoader()`获取默认类加载器

#### SpringFactoriesLoader.loadFactoryNames

```java
//通过读取 META-INF/spring.factories 下的配置，可以获取到相应类需要加载的实现类
public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";

public static List<String> loadFactoryNames(Class<?> factoryClass, @Nullable ClassLoader classLoader) {
    String factoryClassName = factoryClass.getName();
    return loadSpringFactories(classLoader).getOrDefault(factoryClassName, Collections.emptyList());
}
private static Map<String, List<String>> loadSpringFactories(@Nullable ClassLoader classLoader) {
    //用ConcurrentReferenceHashMap做缓存
    MultiValueMap<String, String> result = cache.get(classLoader);
    if (result != null) {
        return result;
    }
    try {
        //通过 classLoader.getResources 解析出 spring.factories 的配置
        Enumeration<URL> urls = (classLoader != null?classLoader.getResources(FACTORIES_RESOURCE_LOCATION):ClassLoader.getSystemResources(FACTORIES_RESOURCE_LOCATION));
        result = new LinkedMultiValueMap<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            UrlResource resource = new UrlResource(url);
            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String factoryClassName = ((String) entry.getKey()).trim();
                for (String factoryName : StringUtils.commaDelimitedListToStringArray((String) entry.getValue())) {
                    result.add(factoryClassName, factoryName.trim());
                }
            }
        }
        cache.put(classLoader, result);
        return result;
    }
    catch (IOException ex) {
        throw new IllegalArgumentException("Unable to load factories from location [" +
                FACTORIES_RESOURCE_LOCATION + "]", ex);
    }
}
```

![image](https://csn-images.oss-cn-shenzhen.aliyuncs.com/markdown/20190312210855.png)

#### createSpringFactoriesInstances

```java
private <T> List<T> createSpringFactoriesInstances(Class<T> type,Class<?>[] parameterTypes, ClassLoader classLoader, Object[] args,Set<String> names) {
    List<T> instances = new ArrayList<>(names.size());
    for (String name : names) {
        try {
            Class<?> instanceClass = ClassUtils.forName(name, classLoader);
            Assert.isAssignable(type, instanceClass);
            Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
            //通过BeanUtils.instantiateClass构建实例对象
            T instance = (T) BeanUtils.instantiateClass(constructor, args);
            instances.add(instance);
        }
        catch (Throwable ex) {
            throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
        }
    }
    return instances;
}
```

#### AnnotationAwareOrderComparator.sort

```java
public static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();
public static void sort(Object[] array) {
    if (array.length > 1) {
        Arrays.sort(array, INSTANCE);
    }
}
```

`AnnotationAwareOrderComparator`是一个比较器，用来比较两个实例的order顺序，实例会实现接口`Ordered`，可以设置实例的顺序。通过`Arrays.sort(数组, 比较器)`来进行*归并算法*的排序

```java
public interface Ordered {
    //MIN_VALUE = 0x80000000
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
    //MAX_VALUE = 0x7fffffff
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;
    int getOrder();
}
public class ContextIdApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    private int order = Ordered.LOWEST_PRECEDENCE - 10;
    public void setOrder(int order) {
        this.order = order;
    }
    @Override
    public int getOrder() {
        return this.order;
    }
}
/**
 * Arrays的归并排序算法mergeSort
 */
public class Arrays {
    public static <T> void sort(T[] a, Comparator<? super T> c) {
        if (c == null) {
            sort(a);
        } else {
            if (LegacyMergeSort.userRequested)
                legacyMergeSort(a, c);
            else
                TimSort.sort(a, 0, a.length, c, null, 0, 0);
        }
    }
    private static <T> void legacyMergeSort(T[] a, Comparator<? super T> c) {
        T[] aux = a.clone();
        if (c==null)
            mergeSort(aux, a, 0, a.length, 0);
        else
            mergeSort(aux, a, 0, a.length, 0, c);
    }
}
```

### 设置成员变量listeners

listeners成员变量，是一个ApplicationListener<?>类型对象的集合。`可以看到获取该成员变量内容使用的是跟成员变量initializers一样的方法`，同样是通过`getSpringFactoriesInstances方法`加载出实例，只不过传入的类型从ApplicationContextInitializer.class变成了ApplicationListener.class

### deduceMainApplicationClass：通过方法堆栈推断

通过`new RuntimeException().getStackTrace()`获取运行时的*执行方法堆栈信息*，以此来推断出运行主类main

```java
private Class<?> deduceMainApplicationClass() {
    try {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if ("main".equals(stackTraceElement.getMethodName())) {
                return Class.forName(stackTraceElement.getClassName());
            }
        }
    }
    catch (ClassNotFoundException ex) {
        // Swallow and continue
    }
    return null;
}
```

`StackTrace`用栈的形式保存了方法的调用信息，可以通过`Thread.currentThread().getStackTrace()`方法来获取堆栈

## SpringApplication对象的run方法

```java
public ConfigurableApplicationContext run(String... args) {
    //简单的秒表计时器
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    ConfigurableApplicationContext context = null;
    Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();
    //设置系统属性java.awt.headless，此处会被设置为true，因为我们开发的是服务器程序，一般运行在没有显示器和键盘的环境
    configureHeadlessProperty();
    //通过getRunListeners获取用于监听SpringApplicationRun的监听实例对象
    SpringApplicationRunListeners listeners = getRunListeners(args);
    listeners.starting();
    try {
        //定义应用参数对象
        ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
        //传入监听器和应用参数，构建环境
        ConfigurableEnvironment environment = prepareEnvironment(listeners,applicationArguments);
        //从环境变量中获取是否忽略beanInfo，放入System变量中
        configureIgnoreBeanInfo(environment);
        //打印SpringBoot的Banner，并获取Banner对象
        Banner printedBanner = printBanner(environment);
        //根据应用类型创建ApplicationContext
        context = createApplicationContext();
        //通过getSpringFactoriesInstances获取SpringBootExceptionReporter的实现类，并出入参数类型
        exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter.class,new Class[] { ConfigurableApplicationContext.class }, context);
        //准备上下文
        prepareContext(context, environment, listeners, applicationArguments,printedBanner);
        //刷新上下文
        refreshContext(context);
        afterRefresh(context, applicationArguments);
        stopWatch.stop();
        if (this.logStartupInfo) {
            new StartupInfoLogger(this.mainApplicationClass)
                    .logStarted(getApplicationLog(), stopWatch);
        }
        listeners.started(context);
        callRunners(context, applicationArguments);
    }
    catch (Throwable ex) {
        handleRunFailure(context, ex, exceptionReporters, listeners);
        throw new IllegalStateException(ex);
    }
    try {
        listeners.running(context);
    }
    catch (Throwable ex) {
        handleRunFailure(context, ex, exceptionReporters, null);
        throw new IllegalStateException(ex);
    }
    return context;
}
```

### SpringApplicationRunListeners

```java
private SpringApplicationRunListeners getRunListeners(String[] args) {
    Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
    return new SpringApplicationRunListeners(logger, getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args));
}

class SpringApplicationRunListeners {
    private final Log log;
    private final List<SpringApplicationRunListener> listeners;
}
```

SpringApplicationRunListeners中的`listeners`同样是通过`getSpringFactoriesInstances方法`加载出实例，传入的接口类型是`SpringApplicationRunListener.class`，并循环开启监听

```java
public void starting() {
    for (SpringApplicationRunListener listener : this.listeners) {
        listener.starting();
    }
}
```

### prepareEnvironment：构建环境

```java
private ConfigurableEnvironment prepareEnvironment(
        SpringApplicationRunListeners listeners,
        ApplicationArguments applicationArguments) {
    // 根据类型创建环境
    ConfigurableEnvironment environment = getOrCreateEnvironment();
    // 配置环境
    configureEnvironment(environment, applicationArguments.getSourceArgs());
    // 将环境当作事件发送给监听器
    listeners.environmentPrepared(environment);
    // 将环境绑定到SpringApplication上
    bindToSpringApplication(environment);
    if (!this.isCustomEnvironment) {
        environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
    }
    // 设置PropertySource为环境的附加属性
    ConfigurationPropertySources.attach(environment);
    return environment;
}
```

#### getOrCreateEnvironment

根据应用类型获取环境

```java
private ConfigurableEnvironment getOrCreateEnvironment() {
    if (this.environment != null) {
        return this.environment;
    }
    switch (this.webApplicationType) {
        case SERVLET:
            return new StandardServletEnvironment();
        case REACTIVE:
            return new StandardReactiveWebEnvironment();
        default:
            return new StandardEnvironment();
    }
}
```

![image](https://csn-images.oss-cn-shenzhen.aliyuncs.com/markdown/20190317212635.png)

```java
public abstract class AbstractEnvironment implements ConfigurableEnvironment {
    protected final Log logger = LogFactory.getLog(getClass());
    private final Set<String> activeProfiles = new LinkedHashSet<>();
    private final Set<String> defaultProfiles = new LinkedHashSet<>(getReservedDefaultProfiles());
    private final MutablePropertySources propertySources = new MutablePropertySources();
    private final ConfigurablePropertyResolver propertyResolver = new PropertySourcesPropertyResolver(this.propertySources);
}
```

#### configureEnvironment

```java
protected void configureEnvironment(ConfigurableEnvironment environment,String[] args) {
    if (this.addConversionService) {
        ConversionService conversionService = ApplicationConversionService.getSharedInstance();
        environment.setConversionService((ConfigurableConversionService) conversionService);
    }
    // 配置环境property
    configurePropertySources(environment, args);
    // 配置环境profile
    configureProfiles(environment, args);
}

protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
    MutablePropertySources sources = environment.getPropertySources();
    if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
        sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));
    }
    if (this.addCommandLineProperties && args.length > 0) {
        String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
        if (sources.contains(name)) {
            PropertySource<?> source = sources.get(name);
            CompositePropertySource composite = new CompositePropertySource(name);
            composite.addPropertySource(new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
            composite.addPropertySource(source);
            sources.replace(name, composite);
        }
        else {
            sources.addFirst(new SimpleCommandLinePropertySource(args));
        }
    }
}

protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
    environment.getActiveProfiles();
    Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
    profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
    environment.setActiveProfiles(StringUtils.toStringArray(profiles));
}
```

configurePropertySources首先查看SpringApplication对象的成员变量defaultProperties，如果该变量非null且内容非空，则将其加入到Environment的PropertySource列表的最后。然后查看SpringApplication对象的成员变量addCommandLineProperties和main函数的参数args，如果设置了addCommandLineProperties=true，且args个数大于0，那么就构造一个由main函数的参数组成的PropertySource放到Environment的PropertySource列表的最前面(这就能保证，我们通过main函数的参数来做的配置是最优先的，可以覆盖其他配置）

#### listeners.environmentPrepared

```java
@Override
public void environmentPrepared(ConfigurableEnvironment environment) {
    this.initialMulticaster.multicastEvent(new ApplicationEnvironmentPreparedEvent(
    this.application, this.args, environment));
}
```

#### ConfigurationPropertySources.attach

将sources封装成了一个名叫configurationProperties的ConfigurationPropertySourcesPropertySource对象，并把这个对象放到了sources的第一个位置。SpringConfigurationPropertySources是一个将MutablePropertySources转换成ConfigurationPropertySources的适配器

### createApplicationContext：创建上下文

根据应用类型创建ApplicationContext对象

```java
protected ConfigurableApplicationContext createApplicationContext() {
    Class<?> contextClass = this.applicationContextClass;
    if (contextClass == null) {
        try {
            switch (this.webApplicationType) {
            case SERVLET:
                contextClass = Class.forName(DEFAULT_SERVLET_WEB_CONTEXT_CLASS);
                break;
            case REACTIVE:
                contextClass = Class.forName(DEFAULT_REACTIVE_WEB_CONTEXT_CLASS);
                break;
            default:
                contextClass = Class.forName(DEFAULT_CONTEXT_CLASS);
            }
        }
        catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Unable create a default ApplicationContext, please specify an ApplicationContextClass", ex);
        }
    }
    return (ConfigurableApplicationContext) BeanUtils.instantiateClass(contextClass);
}
```

### prepareContext：准备上下文

```java
private void prepareContext(ConfigurableApplicationContext context,
        ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
        ApplicationArguments applicationArguments, Banner printedBanner) {
    // 对ApplicationContext设置环境变量
    context.setEnvironment(environment);
    // 配置属性ResourceLoader和ClassLoader属性
    postProcessApplicationContext(context);
    // 循环执行initializers初始化实现类
    applyInitializers(context);
    // 在创建和准备应用程序之后(但在加载源之前)调用，发布contextPrepare事件
    listeners.contextPrepared(context);
    // 打印日志：输出当前运行的profile
    if (this.logStartupInfo) {
        logStartupInfo(context.getParent() == null);
        logStartupProfileInfo(context);
    }
    // 获取上下文bean工厂，并注册单例applicationArguments和printedBanner
    ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
    beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
    if (printedBanner != null) {
        beanFactory.registerSingleton("springBootBanner", printedBanner);
    }
    if (beanFactory instanceof DefaultListableBeanFactory) {
        ((DefaultListableBeanFactory) beanFactory)
                .setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
    }
    // 加载source到应用上下文
    Set<Object> sources = getAllSources();
    Assert.notEmpty(sources, "Sources must not be empty");
    load(context, sources.toArray(new Object[0]));
    // 在加载应用程序上下文之后，但在刷新应用程序上下文之前调用，发布contextLoaded事件
    listeners.contextLoaded(context);
}
```

#### applyInitializers

```java
protected void applyInitializers(ConfigurableApplicationContext context) {
    for (ApplicationContextInitializer initializer : getInitializers()) {
        // 获取接口的唯一泛型
        Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(), ApplicationContextInitializer.class);
        // 判断 提供对象是否是提供类的实例
        Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
        // 传入context初始化
        initializer.initialize(context);
    }
}
```

各个初始化实现类的作用：

![image](https://csn-images.oss-cn-shenzhen.aliyuncs.com/markdown/20190329112903.png)

#### load sources：加载source到应用上下文

BeanDefinitionLoader用于从源加载Bean的定义信息，并封装成BeanDefinition对象，并注册到ApplicationContext中，加载的源可以是*类注解、XML文件、package、classpath、Groovy文件*（多种不同的启动方式）

```java
protected void load(ApplicationContext context, Object[] sources) {
    if (logger.isDebugEnabled()) {
        logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
    }
    // 定义loader对象，并设置环境/参数
    BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
    if (this.beanNameGenerator != null) {
        loader.setBeanNameGenerator(this.beanNameGenerator);
    }
    if (this.resourceLoader != null) {
        loader.setResourceLoader(this.resourceLoader);
    }
    if (this.environment != null) {
        loader.setEnvironment(this.environment);
    }
    loader.load();
}

public int load() {
    int count = 0;
    for (Object source : this.sources) {
        count += load(source);
    }
    return count;
}

private int load(Object source) {
    Assert.notNull(source, "Source must not be null");
    if (source instanceof Class<?>) {
        return load((Class<?>) source);
    }
    if (source instanceof Resource) {
        return load((Resource) source);
    }
    if (source instanceof Package) {
        return load((Package) source);
    }
    if (source instanceof CharSequence) {
        return load((CharSequence) source);
    }
    throw new IllegalArgumentException("Invalid source type " + source.getClass());
}

private int load(Class<?> source) {
    if (isGroovyPresent() && GroovyBeanDefinitionSource.class.isAssignableFrom(source)) {
        // Any GroovyLoaders added in beans{} DSL can contribute beans here
        GroovyBeanDefinitionSource loader = BeanUtils.instantiateClass(source,
        GroovyBeanDefinitionSource.class);
        load(loader);
    }
    if (isComponent(source)) {
        this.annotatedReader.register(source);
        return 1;
    }
    return 0;
}

private boolean isComponent(Class<?> type) {
    // 判断是否包含注解Component
    if (AnnotationUtils.findAnnotation(type, Component.class) != null) {
        return true;
    }
    if (type.getName().matches(".*\\$_.*closure.*") || type.isAnonymousClass()
        || type.getConstructors() == null || type.getConstructors().length == 0) {
        return false;
    }
    return true;
}
```

### refreshContext：刷新上下文

```java
private void refreshContext(ConfigurableApplicationContext context) {
    refresh(context);
    if (this.registerShutdownHook) {
        try {
        context.registerShutdownHook();
        }
        catch (AccessControlException ex) {
        // Not allowed in some environments.
        }
    }
}

protected void refresh(ApplicationContext applicationContext) {
    Assert.isInstanceOf(AbstractApplicationContext.class, applicationContext);
    ((AbstractApplicationContext) applicationContext).refresh();
}
```

```java
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
    // 准备刷新上下文
    prepareRefresh();

    // 让子类去创建一个全新的BeanFactory
    ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

    // 配置工厂的标准上下文特征（上下文的ClassLoader、后处理器）
    prepareBeanFactory(beanFactory);

    try {
        // 允许子类的后置动作去处理BeanFactory
        postProcessBeanFactory(beanFactory);

        // 实例化并调用所有已注册的BeanFactoryPostProcessors
        invokeBeanFactoryPostProcessors(beanFactory);

        // 注册BeanPostProcessors
        registerBeanPostProcessors(beanFactory);

        // 初始化Message资源
        initMessageSource();

        // 初始事件广播器
        initApplicationEventMulticaster();

        // 留给子类初始化其他Bean(空的模板方法)
        onRefresh();

        // 注册事件监听器
        registerListeners();

        // 初始化其他的单例Bean(非延迟加载的)
        finishBeanFactoryInitialization(beanFactory);

        // 完成刷新过程,通知生命周期处理器lifecycleProcessor刷新过程,同时发出ContextRefreshEvent通知
        finishRefresh();
    }

    catch (BeansException ex) {
        if (logger.isWarnEnabled()) {
            logger.warn("Exception encountered during context initialization - " +
            "cancelling refresh attempt: " + ex);
        }

        // 销毁已经创建处理的Bean实例
        destroyBeans();

        // 取消刷新，重置active标记
        cancelRefresh(ex);

        // Propagate exception to caller.
        throw ex;
    }

    finally {
        // 重置Spring核心中的常见内省缓存，因为我们可能不再需要单例bean的元数据了
        resetCommonCaches();
        }
    }
}
```

#### prepareRefresh：准备刷新上下文

```java
protected void prepareRefresh() {
    // Switch to active.
    this.startupDate = System.currentTimeMillis();
    this.closed.set(false);
    this.active.set(true);
    if (logger.isDebugEnabled()) {
        if (logger.isTraceEnabled()) {
            logger.trace("Refreshing " + this);
        }
        else {
            logger.debug("Refreshing " + getDisplayName());
        }
    }
    // 在上下文环境中初始化任何占位符属性源。(空的方法,留给子类覆盖)
    initPropertySources();
    // 验证需要的属性文件是否都已放入环境中
    getEnvironment().validateRequiredProperties();
    // 允许收集早期的应用程序事件，一旦有了多播器，就可以发布
    this.earlyApplicationEvents = new LinkedHashSet<>();
}
```

#### obtainFreshBeanFactory：让子类去创建一个全新的BeanFactory

```java
protected final void refreshBeanFactory() throws BeansException {
    if (hasBeanFactory()) {
        destroyBeans();
        closeBeanFactory();
    }
    try {
        DefaultListableBeanFactory beanFactory = createBeanFactory();
        beanFactory.setSerializationId(getId());
        customizeBeanFactory(beanFactory);
        loadBeanDefinitions(beanFactory);
        synchronized (this.beanFactoryMonitor) {
            this.beanFactory = beanFactory;
        }
    }
    catch (IOException ex) {
        throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
    }
}
```

#### invokeBeanFactoryPostProcessors【重点】：实例化并调用所有已注册的工厂后置处理对象

```java
protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
    // 1.getBeanFactoryPostProcessors(): 拿到当前应用上下文beanFactoryPostProcessors变量中的值
    // 2.invokeBeanFactoryPostProcessors: 实例化并调用所有已注册的BeanFactoryPostProcessor
    PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

    if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }
}
```

* BeanDefinition

BeanDefinition描述了一个bean实例，它具有属性值，构造函数参数值以及具体实现提供的更多信息

* BeanDefinitionRegistry

BeanDefinitionRegistry用于注册BeanDefinition

> 注意

其中有一个PostProcessor就是解析配置了注解的Bean：`ConfigurationClassPostProcessor`，并执行`ConfigurationClassParser.doProcessConfigurationClass()`方法，最终调用`ComponentScanAnnotationParser.parse()`，会扫描所有包下的注入的bean的BeanDefinition，并将其设置到spring的上下文中

```java
public Set<BeanDefinitionHolder> parse(AnnotationAttributes componentScan, final String declaringClass) {
    ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.registry,
            componentScan.getBoolean("useDefaultFilters"), this.environment, this.resourceLoader);
    Class<? extends BeanNameGenerator> generatorClass = componentScan.getClass("nameGenerator");
    boolean useInheritedGenerator = (BeanNameGenerator.class == generatorClass);
    scanner.setBeanNameGenerator(useInheritedGenerator ? this.beanNameGenerator :
            BeanUtils.instantiateClass(generatorClass));
    ScopedProxyMode scopedProxyMode = componentScan.getEnum("scopedProxy");
    if (scopedProxyMode != ScopedProxyMode.DEFAULT) {
        scanner.setScopedProxyMode(scopedProxyMode);
    }
    else {
        Class<? extends ScopeMetadataResolver> resolverClass = componentScan.getClass("scopeResolver");
        scanner.setScopeMetadataResolver(BeanUtils.instantiateClass(resolverClass));
    }
    scanner.setResourcePattern(componentScan.getString("resourcePattern"));
    for (AnnotationAttributes filter : componentScan.getAnnotationArray("includeFilters")) {
        for (TypeFilter typeFilter : typeFiltersFor(filter)) {
            scanner.addIncludeFilter(typeFilter);
        }
    }
    for (AnnotationAttributes filter : componentScan.getAnnotationArray("excludeFilters")) {
        for (TypeFilter typeFilter : typeFiltersFor(filter)) {
            scanner.addExcludeFilter(typeFilter);
        }
    }
    boolean lazyInit = componentScan.getBoolean("lazyInit");
    if (lazyInit) {
        scanner.getBeanDefinitionDefaults().setLazyInit(true);
    }
    Set<String> basePackages = new LinkedHashSet<>();
    String[] basePackagesArray = componentScan.getStringArray("basePackages");
    for (String pkg : basePackagesArray) {
        String[] tokenized = StringUtils.tokenizeToStringArray(this.environment.resolvePlaceholders(pkg),
                ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
        Collections.addAll(basePackages, tokenized);
    }
    for (Class<?> clazz : componentScan.getClassArray("basePackageClasses")) {
        basePackages.add(ClassUtils.getPackageName(clazz));
    }
    if (basePackages.isEmpty()) {
        basePackages.add(ClassUtils.getPackageName(declaringClass));
    }
    scanner.addExcludeFilter(new AbstractTypeHierarchyTraversingFilter(false, false) {
        @Override
        protected boolean matchClassName(String className) {
            return declaringClass.equals(className);
        }
    });
    return scanner.doScan(StringUtils.toStringArray(basePackages));
}

protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
    Assert.notEmpty(basePackages, "At least one base package must be specified");
    Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
    for (String basePackage : basePackages) {
        //此处扫描到包下的注入的bean的BeanDefinition，并将其设置到spring的上下文中
        Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
        for (BeanDefinition candidate : candidates) {
            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
            candidate.setScope(scopeMetadata.getScopeName());
            String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
            if (candidate instanceof AbstractBeanDefinition) {
                postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
            }
            if (candidate instanceof AnnotatedBeanDefinition) {
                AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
            }
            if (checkCandidate(beanName, candidate)) {
                BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                definitionHolder =
                        AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
                beanDefinitions.add(definitionHolder);
                registerBeanDefinition(definitionHolder, this.registry);
            }
        }
    }
    return beanDefinitions;
}
```

#### registerListeners：注册事件监听器

```java
protected void registerListeners() {
    // 首先,注册指定的静态事件监听器,在spring boot中有应用
    for (ApplicationListener<?> listener : getApplicationListeners()) {
        getApplicationEventMulticaster().addApplicationListener(listener);
    }
    // 其次,注册普通的事件监听器
    String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
    for (String listenerBeanName : listenerBeanNames) {
        getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
    }
    // Publish early application events now that we finally have a multicaster...
    Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
    this.earlyApplicationEvents = null;
    if (earlyEventsToProcess != null) {
        for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
            getApplicationEventMulticaster().multicastEvent(earlyEvent);
        }
    }
}
```

#### *finishBeanFactoryInitialization*：初始化其他的单例Bean(非延迟加载的)

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // 判断有无ConversionService(bean属性类型转换服务接口),并初始化
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
            beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
                beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }
    // 如果beanFactory中不包含EmbeddedValueResolver,则向其中添加一个EmbeddedValueResolver
    // EmbeddedValueResolver-->解析bean中的占位符和表达式
    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }
     // 初始化LoadTimeWeaverAware类型的bean
    // LoadTimeWeaverAware-->加载Spring Bean时织入第三方模块,如AspectJ
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }
    // 释放临时类加载器
    beanFactory.setTempClassLoader(null);
    // 冻结缓存的BeanDefinition元数据
    beanFactory.freezeConfiguration();
    // 初始化其他的非延迟加载的单例bean
    beanFactory.preInstantiateSingletons();
}
```

`beanFactory.preInstantiateSingletons()`会根据上下文中扫描到的`BeanDefinition`，来创建单例实例，并放到spring上下文中

```java
public class DefaultListableBeanFactory{
    @Override
    public void preInstantiateSingletons() throws BeansException {
        if (logger.isTraceEnabled()) {
            logger.trace("Pre-instantiating singletons in " + this);
        }

        // Iterate over a copy to allow for init methods which in turn register new bean definitions.
        // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
        List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

        // 触发所有非延迟单例bean的初始化
        for (String beanName : beanNames) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                if (isFactoryBean(beanName)) {
                    Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                    if (bean instanceof FactoryBean) {
                        final FactoryBean<?> factory = (FactoryBean<?>) bean;
                        boolean isEagerInit;
                        if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                            isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
                                            ((SmartFactoryBean<?>) factory)::isEagerInit,
                                    getAccessControlContext());
                        }
                        else {
                            isEagerInit = (factory instanceof SmartFactoryBean &&
                                    ((SmartFactoryBean<?>) factory).isEagerInit());
                        }
                        if (isEagerInit) {
                            getBean(beanName);
                        }
                    }
                }
                else {
                    getBean(beanName);
                }
            }
        }

        // 触发所有适用bean的后初始化回调
        for (String beanName : beanNames) {
            Object singletonInstance = getSingleton(beanName);
            if (singletonInstance instanceof SmartInitializingSingleton) {
                final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
                if (System.getSecurityManager() != null) {
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        smartSingleton.afterSingletonsInstantiated();
                        return null;
                    }, getAccessControlContext());
                }
                else {
                    smartSingleton.afterSingletonsInstantiated();
                }
            }
        }
    }
}
```

#### finishRefresh：完成刷新过程，并做一些通知

```java
protected void finishRefresh() {
    // 清除上下文级资源缓存（例如来自扫描的ASM元数据）
    clearResourceCaches();
    // 为此上下文初始化生命周期处理器
    initLifecycleProcessor();
    // 首先将刷新传播到生命周期处理器
    getLifecycleProcessor().onRefresh();
    // 推送容器刷新事件
    publishEvent(new ContextRefreshedEvent(this));
    // Participate in LiveBeansView MBean, if active.
    LiveBeansView.registerApplicationContext(this);
}
```

> 注意

此处可以编写整个项目启动后要做的事情

```java
@Component
public class FinishRefreshListener implements ApplicationListener<ContextRefreshedEvent> {
    /**
     * 接收到ContextRefreshedEvent事件
     * @param event
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("FinishRefreshListener接收到event：" + event);
    }
}
```

## 工具类

### LogFactory：日志工厂

```java
LogFactory.getLog(FinishRefreshListener.class).info("测试日志：完成");
```

![image](https://csn-images.oss-cn-shenzhen.aliyuncs.com/markdown/20190523113432.png)

### StringUtils：字符串工具类

StringUtils.toStringArray()：Collection转数组

```java
public static String[] toStringArray(Collection<String> collection) {
    return collection.toArray(new String[0]);
}
```

StringUtils.arrayToCommaDelimitedString(sources)：数组转‘,’分隔的字符串

```java
public static String arrayToCommaDelimitedString(@Nullable Object[] arr) {
    return arrayToDelimitedString(arr, ",");
}
```

### BeanUtils：Bean工具类

BeanUtils.instantiateClass()：根据类名创建实例

```java
public static <T> T instantiateClass(Class<T> clazz) throws BeanInstantiationException {
    Assert.notNull(clazz, "Class must not be null");
    if (clazz.isInterface()) {
        throw new BeanInstantiationException(clazz, "Specified class is an interface");
    }
    try {
        return instantiateClass(clazz.getDeclaredConstructor());
    }
    catch (NoSuchMethodException ex) {
        Constructor<T> ctor = findPrimaryConstructor(clazz);
        if (ctor != null) {
            return instantiateClass(ctor);
        }
        throw new BeanInstantiationException(clazz, "No default constructor found", ex);
    }
    catch (LinkageError err) {
        throw new BeanInstantiationException(clazz, "Unresolvable class definition", err);
    }
}
```

### CollectionUtils：Collection工具类

CollectionUtils.isEmpty(xx)：判断是否为空

```java
public static boolean isEmpty(@Nullable Collection<?> collection) {
    return (collection == null || collection.isEmpty());
}
```

### Assert.isInstanceOf：判断 提供对象是提供类的实例

```java
Assert.isInstanceOf(requiredType, context, "Unable to call initializer");
```

### ResolvableType.forClass()：获取接口的具体信息（包含信息泛型等）

```java
ResolvableType resolvableType = ResolvableType.forClass(clazz).as(genericIfc);
```

### AnnotationUtils：注解工具类

AnnotationUtils.findAnnotation()：判断是否包含注解Component

```java
if (AnnotationUtils.findAnnotation(type, Component.class) != null) {
    return true;
}
```

### ObjectUtils：对象工具类

ObjectUtils.isEmpty(this.basePackages)：判断对象是否为空

## 参考文献

- [Sprng ApplicationContext容器refresh过程简析](https://blog.csdn.net/lyc_liyanchao/article/details/83178850)
- [BeanDefinition解析](https://www.cnblogs.com/zhangjianbin/p/9095388.html)