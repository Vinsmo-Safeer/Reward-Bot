package org.bot;

public class Profile {
    private String profileDir;
    private String name;
    private String lastSearchTime;
    private String lastActivityCheck;
    private int timesSearched;

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
}