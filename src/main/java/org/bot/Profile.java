package org.bot;

import java.util.Objects;

public class Profile {
    private String profileDir;
    private String name;
    private String lastSearchTime;
    private String lastActivityCheck;
    private int timesSearched;
    private String lastUsedProxy;
    private String proxyCountry = "";

    // Add Getters and Setters here
    // Example:
    public String getProfileDir() { return profileDir; }
    public void setProfileDir(String profileDir) { this.profileDir = profileDir; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLastSearchTime() { return lastSearchTime; }
    public  void setLastSearchTime (String lastSearchTime) { this.lastSearchTime = lastSearchTime; }

    public String getLastActivityCheck() { return lastActivityCheck; }
    public void setLastActivityCheck(String lastActivityCheck) { this.lastActivityCheck = lastActivityCheck; }

    public int getTimesSearched() { return timesSearched; }
    public void setTimesSearched(int timesSearched) { this.timesSearched = timesSearched; }

    public String getLastUsedProxy() {
        return lastUsedProxy;
    }
    public void setLastUsedProxy(String lastUsedProxy) {
        this.lastUsedProxy = lastUsedProxy;
    }

    public String getProxyCountry() {
        return proxyCountry;
    }
    public void setProxyCountry(String proxyCountry) {
        this.proxyCountry = proxyCountry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return timesSearched == profile.timesSearched &&
                Objects.equals(profileDir, profile.profileDir) &&
                Objects.equals(name, profile.name) &&
                Objects.equals(lastSearchTime, profile.lastSearchTime) &&
                Objects.equals(lastActivityCheck, profile.lastActivityCheck) &&
                Objects.equals(lastUsedProxy, profile.lastUsedProxy) &&   // Added
                Objects.equals(proxyCountry, profile.proxyCountry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileDir, name, lastSearchTime, lastActivityCheck, timesSearched);
    }
}