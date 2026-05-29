package org.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WebshareProxyManager {

    private static final String API_KEY = "wnw7rsxrjigq99kpm2sh94f8e9zf36m5kryg7oms"; // Replace with your token
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        try {
            // 1. Calculate live usage vs max plan capacity
            System.out.println("Calculating Webshare account network usage...");
            displayNetworkHealth();

            // 2. Fetch proxies
            System.out.println("\nFetching proxy items from account pool...");
            String jsonResponse = executeGetRequest("https://proxy.webshare.io/api/v2/proxy/list/?mode=direct&page=1&page_size=100");

            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray results = jsonObject.getAsJsonArray("results");

            int totalProxies = jsonObject.has("count") ? jsonObject.get("count").getAsInt() : results.size();
            System.out.println("\n=== Active Proxies Table (" + totalProxies + " Total) ===");
            System.out.printf("%-22s | %-6s | %-8s | %-12s | %-30s%n",
                    "PROXY ADDRESS", "PORT", "STATUS", "COUNTRY CODE", "ASN / PROVIDER");
            System.out.println("-----------------------------------------------------------------------------------------------");

            for (int i = 0; i < results.size(); i++) {
                JsonObject proxy = results.get(i).getAsJsonObject();
                String ip = proxy.get("proxy_address").getAsString();
                int port = proxy.get("port").getAsInt();
                boolean isValid = proxy.get("valid").getAsBoolean();
                String status = isValid ? "ACTIVE" : "INACTIVE";

                String countryCode = proxy.has("country_code") && !proxy.get("country_code").isJsonNull()
                        ? proxy.get("country_code").getAsString() : "UNKNOWN";
                String asnName = proxy.has("asn_name") && !proxy.get("asn_name").isJsonNull()
                        ? proxy.get("asn_name").getAsString() : "N/A";

                System.out.printf("%-22s | %-6d | %-8s | %-12s | %-30s%n",
                        ip, port, status, countryCode, asnName);
            }
            System.out.println("-----------------------------------------------------------------------------------------------");

        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Pulls total bandwidth constraints and totals aggregate live traffic consumption.
     */
    private static void displayNetworkHealth() throws IOException, InterruptedException {
        // Step A: Get Subscription Plan Allocation Cap
        String subResponse = executeGetRequest("https://proxy.webshare.io/api/v2/subscription/");
        JsonObject subJson = JsonParser.parseString(subResponse).getAsJsonObject();

        if (!subJson.has("plan") || subJson.get("plan").isJsonNull()) {
            System.out.println("Could not discover an active subscription profile.");
            return;
        }

        int planId = subJson.get("plan").getAsInt();
        String planResponse = executeGetRequest("https://proxy.webshare.io/api/v2/subscription/plan/" + planId + "/");
        JsonObject planJson = JsonParser.parseString(planResponse).getAsJsonObject();

        double bandwidthLimitGb = planJson.has("bandwidth_limit") ? planJson.get("bandwidth_limit").getAsDouble() : 0.0;

        // Step B: Fetch Live Aggregated Consumption Stats with filters
        String statsResponse = executeGetRequest("https://proxy.webshare.io/api/v2/stats/");
        JsonArray statsArray = JsonParser.parseString(statsResponse).getAsJsonArray();

        long totalBytesUsed = 0;
        for (int i = 0; i < statsArray.size(); i++) {
            JsonObject statHour = statsArray.get(i).getAsJsonObject();

            // CRITICAL FILTER 1: Ignore future/projected trend calculations
            if (statHour.has("is_projected") && statHour.get("is_projected").getAsBoolean()) {
                continue;
            }

            if (statHour.has("bandwidth_total")) {
                totalBytesUsed += statHour.get("bandwidth_total").getAsLong();
            }
        }

        // Convert raw bytes accumulated to Gigabytes (Standard metric system)
        double totalGigabytesUsed = (double) totalBytesUsed / (1024 * 1024 * 1024);

        // Step C: Render a formatted output
        System.out.println("-------------------------------------------------------");
        if (bandwidthLimitGb == 0.0) {
            System.out.printf(">>> Network Status: %.2f GB Used / UNLIMITED Allocated%n", totalGigabytesUsed);
        } else {
            double percentUsed = (totalGigabytesUsed / bandwidthLimitGb) * 100;
            System.out.printf(">>> Network Status: %.1f GB / %.1f GB Used (%.1f%%)%n",
                    totalGigabytesUsed, bandwidthLimitGb, percentUsed);

            // Match dashboard red warning banner condition
            if (totalGigabytesUsed >= bandwidthLimitGb) {
                System.out.println("ALERT: You have used up all your bandwidth allowance! Proxies are inactive.");
            }
        }
        System.out.println("-------------------------------------------------------");
    }

    /**
     * Shared helper to run authenticated API requests
     */
    private static String executeGetRequest(String targetUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Token " + API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Connection Refused. HTTP Status: "
                    + response.statusCode() + " | Body: " + response.body());
        }
        return response.body();
    }
}