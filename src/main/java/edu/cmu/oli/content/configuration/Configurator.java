package edu.cmu.oli.content.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import java.io.FileReader;
import java.io.IOException;

/**
 * CDI bean that emits service configurations properties hosted on a distributed
 * hazelcast cache.
 *
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class Configurator {

    public static final String PROPERTY_NAME = "CONTENT_SERVICE_CONFIG";
    // public static final boolean IS_OLD_VALUE_REQUIRED = false;
    // public static final boolean IS_SYNCHRONOUS = true;
    // public final static String CONFIGURATION = "content_conf";
    //
    // static {
    // String logging = "hazelcast.logging.type";
    // if (System.getProperty(logging) == null) {
    // System.setProperty(logging, "jdk");
    // }
    // System.setProperty("hazelcast.wait.seconds.before.join", "1");
    // System.setProperty("hazelcast.jcache.provider.type", "server");
    // System.setProperty("hazelcast.health.monitoring.level", "OFF");
    // }
    //
    // Cache<String, String> store;
    // CachingProvider cachingProvider;
    // CacheManager cacheManager;
    private Configurations configurations;

    /**
     * Initial set of key-values can be exposed with @Produces and merged at startup
     * with the cache. The existing entries in the cache are going to be overridden.
     */
    public void init(@Observes @Initialized(ApplicationScoped.class) Object doesntMatter) {
        doInit();
    }

    private void doInit() {
        // this.cachingProvider = Caching.getCachingProvider();
        // this.cacheManager = cachingProvider.getCacheManager();
        // Configuration<String, String> configuration = getConfigurationStore();
        // this.store = this.cacheManager.getCache(CONFIGURATION, String.class,
        // String.class);
        // if (this.store == null) {
        // this.store = this.cacheManager.createCache(CONFIGURATION, configuration);
        // }
        // loadConfiguration();
        // //this.store.putAll(getInitial());
        // CacheEntryListenerConfiguration listenerConfigurationWithFilter =
        // new
        // MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(ContentCacheEntryListener.class),
        // FactoryBuilder.factoryOf(ContentCacheEntryEventFilter.class),
        // IS_OLD_VALUE_REQUIRED,
        // IS_SYNCHRONOUS);
    }

    @Produces
    @ConfigurationCache
    public Configurations getServiceConfiguration() {
        if (configurations == null) {
            loadConfiguration();
        }
        return configurations;
    }

    /**
     * Loads service specific configurations from default file system location
     */
    private void loadConfiguration() {
        String path = System.getenv().get(PROPERTY_NAME);
        try (FileReader fileReader = new FileReader(path)) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(fileReader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            this.configurations = createConfiguration(jsonObject);
            // Store the new configurations in distributed cache
            // if (store != null) {
            // store.put("content.service.config", AppUtils.gsonBuilder().create().toJson(jsonObject));
            // }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Loads configurations from the distributed cache
     *
     * @param jsonString
     */
    private void loadConfiguration(String jsonString) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(jsonString);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        this.configurations = createConfiguration(jsonObject);
    }

    public static Configurations createConfiguration(JsonObject configurationObject) {
        Configurations config = new Configurations();
        String directory = configurationObject.get("contentSourceXml").getAsString();
        config.setContentSourceXml(directory);
        config.setContentVolume(configurationObject.get("contentVolume").getAsString());
        config.setWebContentVolume(configurationObject.get("webContentVolume").getAsString());
        if (configurationObject.has("themesRepository")) {
            config.setThemesRepository(configurationObject.get("themesRepository").getAsString());
        }
        if (configurationObject.has("transactionRetrys")) {
            config.setTransactionRetrys(configurationObject.get("transactionRetrys").getAsInt());
        }
        if (configurationObject.has("editLockMaxDuration")) {
            config.setEditLockMaxDuration(configurationObject.get("editLockMaxDuration").getAsInt());
        }
        if (configurationObject.has("themes")) {
            config.setThemes(configurationObject.get("themes").getAsJsonArray());
        }
        if (configurationObject.has("previewServers")) {
            config.setPreviewServers(configurationObject.get("previewServers").getAsJsonArray());
        }
        JsonObject deploymentRequest = configurationObject.has("deploymentRequest")?
                configurationObject.get("deploymentRequest").getAsJsonObject(): null;
        if(deploymentRequest !=  null) {
            if(deploymentRequest.has("token")){
                config.setEmailServerToken(deploymentRequest.get("token").getAsString());
            }

            if(deploymentRequest.has("fromEmail")){
                config.setEmailFrom(deploymentRequest.get("fromEmail").getAsString());
            }

            if (deploymentRequest.has("emailServer")) {
                config.setEmailServer(deploymentRequest.get("emailServer").getAsString());
            }

            if (deploymentRequest.has("deployRequestEmails")) {
                JsonArray deployRequestEmails = deploymentRequest.get("deployRequestEmails").getAsJsonArray();
                deployRequestEmails.forEach(jsonElement -> config.addDeployRequestEmail(jsonElement.getAsString()));
            }
        }
        JsonObject developerAdmin = configurationObject.getAsJsonObject("developerAdmin");
        config.setDeveloperAdmin(developerAdmin);
        config.setNamespaces(configurationObject.getAsJsonObject("namespaces"));
        JsonArray resourceTypes = configurationObject.getAsJsonArray("resourceTypes");
        resourceTypes.forEach((val) -> {
            JsonObject resourceType = (JsonObject) val;
            config.addResourceType(resourceType.get("id").getAsString(), resourceType);
        });
        if (configurationObject.has("contentServiceDebugEnabled")) {
            config.setContentServiceDebugEnabled(configurationObject.get("contentServiceDebugEnabled").getAsBoolean());
        }
        return config;
        // this.configurations = config;
    }

    // private Configuration<String, String> getConfigurationStore() {
    // MutableConfiguration<String, String> configuration = new
    // MutableConfiguration<>();
    // configuration.setStoreByValue(true).
    // setTypes(String.class, String.class).
    // setManagementEnabled(false).
    // setStatisticsEnabled(false);
    // return configuration;
    // }

    // public void shutdown(@Observes @Destroyed(ApplicationScoped.class) Object
    // doesntMatter) {
    // this.cacheManager.destroyCache(CONFIGURATION);
    // this.cachingProvider.close();
    // }
    //
    // class ContentCacheEntryListener implements CacheEntryCreatedListener<String,
    // String>,
    // CacheEntryUpdatedListener<String, String> {
    //
    // public void onCreated(Iterable<CacheEntryEvent<? extends String, ? extends
    // String>> cacheEntryEvents) throws CacheEntryListenerException {
    // for (CacheEntryEvent entryEvent : cacheEntryEvents) {
    // if ("content.service.config".equals(entryEvent.getKey())) {
    // loadConfiguration((String) entryEvent.getValue());
    // }
    // //System.out.println("Created : " + entryEvent.getKey() + " with value : " +
    // entryEvent.getValue());
    // }
    // }
    //
    // public void onUpdated(Iterable<CacheEntryEvent<? extends String, ? extends
    // String>> cacheEntryEvents) throws CacheEntryListenerException {
    // for (CacheEntryEvent entryEvent : cacheEntryEvents) {
    // if ("content.service.config".equals(entryEvent.getKey())) {
    // loadConfiguration((String) entryEvent.getValue());
    // }
    // //System.out.println("Updated : " + entryEvent.getKey() + " with value : " +
    // entryEvent.getValue());
    // }
    // }
    // }
}
