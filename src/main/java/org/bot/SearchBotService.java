package org.bot;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class SearchBotService {

    private final Settings settings;
    private final Random random = new Random();

    public SearchBotService(Settings settings) {
        this.settings = settings;
    }

    /**
     * Executes a single automated random search query over standard Google Chrome.
     * @param profileDir The target browser profile directory name.
     */
    public void executeSearch(String profileDir) {
        String url = generateBingSearchUrl();
        System.out.println("Starting Chrome automation profile...");

        try {
            new ProcessBuilder(
                    settings.getChromePath(),
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + settings.getUserDataDir(),
                    "--profile-directory=" + profileDir,
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--excludeSwitches=enable-automation",
                    url
            ).start();

            int delaySeconds = 10 + random.nextInt(5);
            System.out.println("Waiting " + delaySeconds + " seconds for page load render...");
            Thread.sleep(delaySeconds * 1000L);

            System.out.println("Terminating Chrome instances...");
            nukeChrome();
            System.out.println("Chrome instance cleaned.");
        } catch (Exception e) {
            System.err.println("Error executing search bot loop: " + e.getMessage());
        }
    }

    public void nukeChrome() {
        try {
            ProcessBuilder pb = settings.getOs().toLowerCase().contains("win")
                    ? new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe", "/T")
                    : new ProcessBuilder("pkill", "-9", "chrome");
            pb.start().waitFor();
        } catch (Exception e) {
            System.err.println("Could not nuke Chrome: " + e.getMessage());
        }
    }

    private String generateBingSearchUrl() {
        String query = getRandomQuery();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");

        String url = "https://www.bing.com/search?q=" + encodedQuery + "&form=QBRE&qs=AS";
        System.out.println("Generated Targeted Automation URL: " + url);
        return url;
    }

    private String getRandomQuery() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("assets/Queries.txt"));
            if (!lines.isEmpty()) {
                return lines.get(random.nextInt(lines.size()));
            }
            System.out.println("Warning: Queries file is empty.");
        } catch (IOException e) {
            System.err.println("Failed to read queries file assets/Queries.txt: " + e.getMessage());
        }
        return "Microsoft Rewards"; // Resilient baseline fallback search
    }
}