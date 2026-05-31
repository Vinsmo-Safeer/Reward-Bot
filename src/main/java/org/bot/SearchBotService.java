package org.bot;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SearchBotService {

    DatabaseManager db;
    ProxyManger proxyManger;

    private final Settings settings;
    private final Random random = new Random();

    public SearchBotService(Settings settings, DatabaseManager db) {
        this.settings = settings; this.db = db;
    }

    public String executeSearch(Profile profile) {

        String url = generateBingSearchUrl();
        System.out.println("Starting Chrome automation profile...");

        String proxyInfo = "";
        try {

        if (settings.isEnableProxy()) {
            proxyManger = new ProxyManger(settings);
            proxyManger.loadingProxies();
            Proxy proxy = proxyManger.getAppropriateProxy(profile.getProfileDir(), db, profile);

            if (proxy == null) {
                System.out.println("No proxy available for profile '" + profile.getProfileDir() + "', skipping search.");
                return null;
            }
            new ProcessBuilder(
                    settings.getChromePath(),
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + settings.getUserDataDir(),
                    "--profile-directory=" + profile.getProfileDir(),
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--excludeSwitches=enable-automation",

                    // ADD THE PROXY HERE (Choose the format that fits your needs):
                    "--proxy-server=http://" + proxy.getProxyAddress() + ":" + proxy.getPort(),

                    url
            ).start();

            proxyInfo = proxy.getProxyAddress() + "_" + proxy.getCountryCode();


        } else {
            new ProcessBuilder(
                    settings.getChromePath(),
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + settings.getUserDataDir(),
                    "--profile-directory=" + profile.getProfileDir(),
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--excludeSwitches=enable-automation",

                    url
            ).start();
        }




            int delaySeconds = 10 + random.nextInt(5);
            System.out.println("Waiting " + delaySeconds + " seconds for page load render...");
            Thread.sleep(delaySeconds * 1000L);

            System.out.println("Terminating Chrome instances...");
            nukeChrome();
            System.out.println("Chrome instance cleaned.");
        } catch (Exception e) {
            System.err.println("Error executing search bot loop: " + e.getMessage());
        }

        if (settings.isEnableProxy()) {
            return "0 " + proxyInfo; // No error, but include proxy info for saving to DB
        } else {
            return "0";
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

    private void initializeQueriesFile() throws IOException {
        // Create the parent directory (assets/) if it doesn't exist
        if (Paths.get(settings.getQueriesFilePath()).getParent() != null) {
            Files.createDirectories(Paths.get(settings.getQueriesFilePath()).getParent());
        }

        // Create the file if it doesn't exist
        if (Files.notExists(Paths.get(settings.getQueriesFilePath()))) {
            Files.createFile(Paths.get(settings.getQueriesFilePath()));
        }

        // If the file is empty, populate it with the default queries
        if (Files.size(Paths.get(settings.getQueriesFilePath())) == 0) {
            List<String> defaultQueries = Arrays.asList(
                    "Why is the sky blue during the day?", "How to learn programming faster?",
                    "What is artificial intelligence in simple terms?", "Why do cats purr?",
                    "How does the internet actually work?", "What causes earthquakes to happen?",
                    "How to improve focus while studying?", "Why do sunsets appear red or orange?",
                    "What is cloud computing and how is it used?", "Why do humans need sleep?",
                    "How to stop procrastinating consistently?", "How do plants perform photosynthesis?",
                    "What is the meaning of consciousness?", "How do computers process data?",
                    "Why is water essential for life?", "How to manage time effectively as a student?",
                    "How does gravity work on Earth?", "Can time travel ever be possible?",
                    "How do encryption algorithms keep data secure?", "Why do seasons change throughout the year?",
                    "How to build good daily habits?", "What happens after death scientifically?",
                    "How does the human brain store memory?", "Are we alone in the universe?",
                    "Why do humans seek purpose in life?", "Why do stars twinkle in the night sky?",
                    "How to stay consistent with daily routines?", "What is machine learning and how does it work?",
                    "Why do dogs wag their tails?", "How does GPS determine location?",
                    "What causes lightning and thunder?", "How to improve memory and retention?",
                    "Why is the ocean salty?", "What is blockchain technology in simple terms?",
                    "Why do humans dream at night?", "How to wake up early without feeling tired?",
                    "How do vaccines work in the body?", "What is dark matter in the universe?",
                    "How do smartphones connect to the internet?", "Why do leaves change color in autumn?",
                    "How to stay motivated for long-term goals?", "How does the stock market work for beginners?",
                    "Can humans live on Mars in the future?", "How do passwords get hacked?",
                    "Why does ice float on water?", "How to reduce stress and anxiety naturally?",
                    "What is quantum computing in simple terms?", "How do airplanes stay in the air?",
                    "Why do humans feel emotions?", "How to build discipline in daily life?",
                    "Why do we yawn when we are tired?", "How to improve communication skills?",
                    "What is the difference between AI and machine learning?", "Why do we get hiccups?",
                    "How does Wi-Fi work in a house?", "What causes volcanic eruptions?",
                    "How to stay focused for long hours?", "Why is the moon visible during the day sometimes?",
                    "What is cybersecurity and why is it important?", "Why do humans laugh?",
                    "How to break bad habits permanently?", "How does digestion work in the human body?",
                    "What is the theory of relativity in simple terms?", "How do search engines rank websites?",
                    "Why does metal rust over time?", "How to improve problem-solving skills?",
                    "How does electricity flow in a circuit?", "Can artificial intelligence replace humans?",
                    "How do hackers exploit vulnerabilities?", "Why do we feel hungry?",
                    "How to develop critical thinking skills?", "What is the Big Bang theory?",
                    "How does Bluetooth technology work?", "Why do people procrastinate?",
                    "How to become more productive every day?", "Why do we sneeze?",
                    "How to improve typing speed?", "What is deep learning and how is it used?",
                    "Why do mirrors reflect images?", "How does a VPN work?",
                    "What causes tides in the ocean?", "How to stay disciplined without motivation?",
                    "Why is the sun so hot?", "What is data science and what do data scientists do?",
                    "Why do people fear public speaking?", "How to learn faster and retain information?",
                    "How does the immune system fight infections?", "What is string theory in simple terms?",
                    "How do operating systems work?", "Why do we feel pain?",
                    "How to build confidence from scratch?", "How does nuclear energy work?",
                    "Can humans achieve immortality?", "How do phishing attacks work?",
                    "Why do we get goosebumps?", "How to improve decision-making skills?",
                    "What is augmented reality and how does it work?", "How do satellites stay in orbit?",
                    "Why do humans form habits?", "How to stay consistent with long-term goals?"
            );
            Files.write(Paths.get(settings.getQueriesFilePath()), defaultQueries);
        }
    }

    private String getRandomQuery() {
        try {
            // Ensure the file exists and is populated before reading
            initializeQueriesFile();

            List<String> lines = Files.readAllLines(Paths.get(settings.getQueriesFilePath()));
            if (!lines.isEmpty()) {
                return lines.get(random.nextInt(lines.size()));
            }
        } catch (IOException e) {
            System.err.println("Failed to handle queries file " + Paths.get(settings.getQueriesFilePath()) + ": " + e.getMessage());
        }
        return "Microsoft Rewards"; // Resilient baseline fallback search
    }
}