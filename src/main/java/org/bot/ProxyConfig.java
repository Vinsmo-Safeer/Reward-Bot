package org.bot;

import java.util.List;

public class ProxyConfig {
    private boolean useStaticProvider;
    private List<String> staticProxies;
    private boolean useWebshareApi;
    private List<String> webshareKeys;

    // Getters
    public boolean isUseStaticProvider() { return useStaticProvider; }
    public List<String> getStaticProxies() { return staticProxies; }
    public boolean isUseWebshareApi() { return useWebshareApi; }
    public List<String> getWebshareKeys() { return webshareKeys; }

    // Setters (Required by Jackson)
    public void setUseStaticProvider(boolean useStaticProvider) { this.useStaticProvider = useStaticProvider; }
    public void setStaticProxies(List<String> staticProxies) { this.staticProxies = staticProxies; }
    public void setUseWebshareApi(boolean useWebshareApi) { this.useWebshareApi = useWebshareApi; }
    public void setWebshareKeys(List<String> webshareKeys) { this.webshareKeys = webshareKeys; }
}