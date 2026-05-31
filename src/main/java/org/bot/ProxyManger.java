package org.bot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public class ProxyManger {

    Settings settings;

    List<Proxy> proxies;

    WebshareProxyManager webshareProxyManager;

    public ProxyManger(Settings settings) {
        this.settings = settings;
    }

    public void loadingProxies() {

        System.out.println("Loading proxies...");

        proxies = new java.util.ArrayList<>();

        if (settings.getProxyConfig().isUseStaticProvider()) {
            setStaticProxies();
        } else if (settings.getProxyConfig().isUseWebshareApi()) {
            setWebshareProxies();
        } else {
            System.err.println("No proxy provider configured. Please check your settings. If you don't want to use proxies, please disable 'enableProxy' in config.yaml");
            Main.running = false;
        }

        // Print loaded proxies for debugging
        System.out.println("Loaded Proxies:");
        for (Proxy proxy : proxies) {
            if (proxy.isStaticProxy()) { System.out.println("[Static] " + proxy.getProxyAddress() + ":" + proxy.getPort()); }
            else { System.out.println(proxy.getProxyAddress() + ":" + proxy.getPort() + " - " + proxy.getStatus() + " - " + proxy.getCountryCode() + " - Usage Left: " + proxy.getUsageLeft() + "GB"); }
        }
    }

    public Proxy getAppropriateProxy(String profileDir, DatabaseManager db, Profile profile) {
        // Load all profiles from the database
        List<Profile> allProfiles = db.loadAllProfiles();

        if (profile == null) {
            System.err.println("Warning: Profile directory not found in database: " + profileDir);
            return null;
        }

        // Check if a proxy is already assigned and still valid
        String lastProxyStr = profile.getLastUsedProxy();
        if (lastProxyStr != null && !lastProxyStr.equals("0.0.0.0") && !lastProxyStr.isEmpty()) {
            Proxy existingProxy = findProxyInList(lastProxyStr);
            if (existingProxy != null && isProxyValid(existingProxy)) {
                return existingProxy;
            }
        }

        // If no valid proxy found, look for an unassigned one
        Proxy availableProxy = findUnassignedProxy(allProfiles, profile);
        if (availableProxy != null) {
            return availableProxy;
        }

        System.err.println("Warning: No available proxies left to assign for profile: " + profileDir);
        return null;
    }

    // --- Helper methods for your logic ---

    private Proxy findProxyInList(String proxyStr) {
        for (Proxy p : proxies) {
            String address = p.getProxyAddress();
            if (address.equals(proxyStr)) {
                return p;
            }
        }
        return null;
    }

    private boolean isProxyValid(Proxy proxy) {
        // Implement your validity check here (e.g., check custom status or usage left)
        if (proxy.isStaticProxy()) return true;
        return proxy.getUsageLeft() > 0;
    }

    private Proxy findUnassignedProxy(List<Profile> allProfiles, Profile profile) {
        // 1. Locate the target profile to assess its time constraints
        if (profile == null) return null;

        // 2. Calculate hours since activity
        long hoursSinceSearch = getHoursSince(profile.getLastSearchTime());
        long hoursSinceActivity = getHoursSince(profile.getLastActivityCheck());

        // Choose the threshold context (e.g., maximum or minimum elapsed time based on your business rule)
        long maxHoursElapsed = Math.max(hoursSinceSearch, hoursSinceActivity);
        String lastCountry = profile.getProxyCountry();

        // 3. Evaluate criteria checks based on elapsed hours
        for (Proxy p : proxies) {
            String proxyStr = p.getProxyAddress();

            // Ensure proxy is not used by any other active profile
            boolean isAssigned = false;
            for (Profile prof : allProfiles) {
                if (proxyStr.equals(prof.getLastUsedProxy())) {
                    isAssigned = true;
                    break;
                }
            }

            if (!isAssigned && isProxyValid(p)) {
                if (Objects.equals(lastCountry, "")) {
                    // If we have no country info, we can assign any valid proxy
                    return p;
                } else {
                    // Condition A: If elapsed time is between 4 and 24 hours, stick STRICTLY to the same country code
                    if (maxHoursElapsed >= 24) {
                        return p;
                    }
                    // 2. If it's between 4 and 24 hours, ONLY assign if the country matches
                    else if (maxHoursElapsed >= 4) {
                        if (p.getCountryCode() != null && p.getCountryCode().equalsIgnoreCase(lastCountry)) {
                            return p;
                        }
                        // If the country doesn't match, we skip this proxy and check the next one in the loop
                    }
                    // 3. If it has been less than 4 hours, we skip assigning entirely
                    else {
                        // "Else skip" - do nothing here, let the loop continue
                    }
                }
            }
        }
        return null;
    }

    /**
     * Helper method to cleanly extract elapsed hours from default date strings
     */
    private long getHoursSince(String dateStr) {
        if (dateStr == null || dateStr.equals("00-00-0000 00:00:00") || dateStr.isEmpty()) {
            return Long.MAX_VALUE; // Treating uninitialized dates as an infinite timeout (exceeds 24 hours)
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
            LocalDateTime pastTime = LocalDateTime.parse(dateStr, formatter);
            return ChronoUnit.HOURS.between(pastTime, LocalDateTime.now());
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    public void setStaticProxies() {
        String[] proxiesGiven = settings.getProxyConfig().getStaticProxies().toArray(new String[0]);
        for (String proxy : proxiesGiven) {

            // Separate IP and port if given in "IP:Port" format
            String[] parts = proxy.split(":");

            Proxy p = new Proxy(parts[0].trim());
            p.setPort(parts.length > 1 ? parts[1].trim() : "80");
            p.setStaticProxy(true);
            proxies.add(p);
        }
    }

    public void setWebshareProxies() {
        webshareProxyManager = new WebshareProxyManager(settings);
        for (String key : settings.getProxyConfig().getWebshareKeys()) {
            List<Proxy> webshareProxies = webshareProxyManager.getActiveProxiesFrom(key);
            if (webshareProxies != null) {
                proxies.addAll(webshareProxies);
            } else {
                System.err.println("Failed to fetch proxies for key: " + key);
            }
        }
    }

}
