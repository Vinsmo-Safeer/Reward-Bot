package org.bot;


public class Settings {

    // General Settings
    private String os;
    private int maxSearchPerDay;

    // Paths
    private String chromePath;
    private String userDataDirectoryPath;
    private String queriesFilePath;
    private String databasePath;
    private String databaseBackupPath;
    private ProfilesConfig profiles;


    // Proxy
    private boolean EnableProxy;
    private ProxyConfig proxySettings;

    public String getOs() {
        return os;
    }public void setOs(String os) {
        this.os = os;
    }
    public int getMaxSearchPerDay() {
        return maxSearchPerDay;
    }public void setMaxSearchPerDay(int maxSearchPerDay) {
        this.maxSearchPerDay = maxSearchPerDay;
    }

    public String getChromePath() {
        return chromePath;
    }public void setChromePath(String chromePath) {
        this.chromePath = chromePath;
    }
    public String getUserDataDir() {
        return userDataDirectoryPath;
    }public void setUserDataDir(String userDataDirectoryPath) {
        this.userDataDirectoryPath = userDataDirectoryPath;
    }
    public String getQueriesFilePath() {
        return queriesFilePath;
    }public void setQueriesFilePath(String queriesFilePath) {
        this.queriesFilePath = queriesFilePath;
    }
    public String getDatabasePath() {
        return databasePath;
    }public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }
    public String getDatabaseBackupPath() {
        return databaseBackupPath;
    }public void setDatabaseBackupPath(String databaseBackupPath) {
        this.databaseBackupPath = databaseBackupPath;
    }
    public ProfilesConfig getProfiles() {
        return profiles;
    }public void setProfiles(ProfilesConfig profiles) {
        this.profiles = profiles;
    }

    public boolean isEnableProxy() {
        return EnableProxy;
    }public void setEnableProxy(boolean enableProxy) {
        EnableProxy = enableProxy;
    }
    public ProxyConfig getProxyConfig() {
        return proxySettings;
    }public void setProxySettings(ProxyConfig proxySettings) {
        this.proxySettings = proxySettings;
    }


}
