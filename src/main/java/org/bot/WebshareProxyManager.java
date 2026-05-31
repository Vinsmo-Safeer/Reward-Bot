package org.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class WebshareProxyManager {

    private final Settings settings;
    private final HttpClient client;

    public WebshareProxyManager(Settings settings) {
        this.settings = settings;
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Fetches proxies from the Webshare API, calculates remaining bandwidth capacity,
     * builds and populates Proxy domain objects, and returns them as a list.
     *
     * @param apiKey The Webshare API authentication token.
     * @return List of populated Proxy objects.
     */
    /**
     * Fetches all proxies returned by the API and maps them to a list of Proxy objects.
     *
     * @param apiKey The Webshare API authentication token.
     * @return List of all retrieved Proxy objects.
     */
    public List<Proxy> getProxiesFrom(String apiKey) {
        List<Proxy> proxyList = new ArrayList<>();

        try {
            // 1. Calculate remaining bandwidth (Usage Left)
            double usageLeftGb = calculateRemainingUsage(apiKey);

            // 2. Fetch proxies from account pool
            String targetUrl = "https://proxy.webshare.io/api/v2/proxy/list/?mode=direct&page=1&page_size=100";
            String jsonResponse = executeGetRequest(targetUrl, apiKey);

            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray results = jsonObject.getAsJsonArray("results");

            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JsonObject proxyJson = results.get(i).getAsJsonObject();

                    // Webshare proxy unique ID/address
                    String ip = proxyJson.get("proxy_address").getAsString();
                    Proxy proxy = new Proxy(ip);

                    // Map fields using Proxy setters
                    proxy.setPort(String.valueOf(proxyJson.get("port").getAsInt()));

                    boolean isValid = proxyJson.get("valid").getAsBoolean();
                    proxy.setStatus(isValid ? "ACTIVE" : "INACTIVE");

                    String countryCode = proxyJson.has("country_code") && !proxyJson.get("country_code").isJsonNull()
                            ? proxyJson.get("country_code").getAsString() : "UNKNOWN";
                    proxy.setCountryCode(countryCode);

                    // Pass down the global remaining usage calculated for this cycle
                    proxy.setUsageLeft(usageLeftGb);

                    // Default setting based on architecture requirements
                    proxy.setStaticProxy(false);

                    proxyList.add(proxy);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch or map proxy pool: " + e.getMessage());
            e.printStackTrace();
        }

        return proxyList;
    }

    /**
     * Validates aggregate bandwidth allocation conditions and extracts functional active nodes.
     *
     * @param apiKey The Webshare API authentication token.
     * @return Filtered List containing only usable, active Proxies.
     */
    public List<Proxy> getActiveProxiesFrom(String apiKey) {
        List<Proxy> activeProxies = new ArrayList<>();
        List<Proxy> allProxies = getProxiesFrom(apiKey);

        // Guard Clause / Check: If the list is empty, usage left couldn't be calculated or request failed
        if (allProxies.isEmpty()) {
            return activeProxies;
        }

        // Capture the calculated remaining usage from the first item metadata
        double usageLeftGb = allProxies.get(0).getUsageLeft();

        // Check condition: Bandwidth limit is not reached (remaining allowance > 0)
        if (usageLeftGb > 0) {
            for (Proxy proxy : allProxies) {
                // Check status constraint
                if ("ACTIVE".equalsIgnoreCase(proxy.getStatus())) {
                    activeProxies.add(proxy);
                }
            }
        } else {
            System.out.println("ALERT: Bandwidth limit has been reached of API key: " + apiKey + ". No active proxies will be returned.");
        }

        return activeProxies;
    }

    /**
     * Pulls total network constraints and calculates left over bandwidth allocations in Gigabytes.
     */
    private double calculateRemainingUsage(String apiKey) throws IOException, InterruptedException {
        // Fetch plan allocation cap
        String subResponse = executeGetRequest("https://proxy.webshare.io/api/v2/subscription/", apiKey);
        JsonObject subJson = JsonParser.parseString(subResponse).getAsJsonObject();

        if (!subJson.has("plan") || subJson.get("plan").isJsonNull()) {
            return 0.0; // Assume 0 if no explicit subscription profile is found
        }

        int planId = subJson.get("plan").getAsInt();
        String planResponse = executeGetRequest("https://proxy.webshare.io/api/v2/subscription/plan/" + planId + "/", apiKey);
        JsonObject planJson = JsonParser.parseString(planResponse).getAsJsonObject();

        double bandwidthLimitGb = planJson.has("bandwidth_limit") ? planJson.get("bandwidth_limit").getAsDouble() : 0.0;

        // If plan is set to unlimited
        if (bandwidthLimitGb == 0.0) {
            return Double.MAX_VALUE;
        }

        // Fetch live aggregated consumption stats
        String statsResponse = executeGetRequest("https://proxy.webshare.io/api/v2/stats/", apiKey);
        JsonArray statsArray = JsonParser.parseString(statsResponse).getAsJsonArray();

        long totalBytesUsed = 0;
        for (int i = 0; i < statsArray.size(); i++) {
            JsonObject statHour = statsArray.get(i).getAsJsonObject();

            // Ignore future/projected trend calculations
            if (statHour.has("is_projected") && statHour.get("is_projected").getAsBoolean()) {
                continue;
            }

            if (statHour.has("bandwidth_total")) {
                totalBytesUsed += statHour.get("bandwidth_total").getAsLong();
            }
        }

        double totalGigabytesUsed = (double) totalBytesUsed / (1024 * 1024 * 1024);
        double usageLeft = bandwidthLimitGb - totalGigabytesUsed;

        return usageLeft > 0 ? usageLeft : 0.0;
    }

    /**
     * Shared helper to run authenticated API requests
     */
    /**
     * Shared helper to run authenticated API requests
     */
    private String executeGetRequest(String targetUrl, String apiKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Token " + apiKey)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Handle authentication failure explicitly
        if (response.statusCode() == 401) {
            System.err.println("\n[ERROR] Authentication Failed: The provided Webshare API key is invalid, inactive, or expired.");
            System.err.println("[HELP]  Attempted API Key: " + apiKey);
            System.err.println("[HELP]  Please verify your credentials or configure IP Authorization by adding this machine's public IP address in your Webshare dashboard.");
            System.err.println("[HELP]  Alternatively, you can disable proxies by setting 'useProxy: false' in your config.yaml file.\n");

            System.err.println("CRITICAL: API authentication failed (HTTP 401). Terminating process...");
            Main.running = false;
            return ""; // Return empty or handle gracefully depending on method signature requirements
        }

        if (response.statusCode() != 200) {
            System.err.println("\n[ERROR] API Connection Refused. HTTP Status: " + response.statusCode());
            System.err.println("[HELP]  Attempted API Key: " + apiKey);
            System.err.println("[DEBUG] Response Body: " + response.body() + "\n");
        }
        return response.body();
    }
}