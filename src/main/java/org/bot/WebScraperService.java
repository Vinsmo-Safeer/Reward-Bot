package org.bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WebScraperService {

    private static final String TARGET_URL = "https://rewards.bing.com/earn";
    private static final String DEBUG_URL = "http://127.0.0.1:9222";

    private final Settings settings;
    private final Random random = new Random();

    public WebScraperService(Settings settings) {
        this.settings = settings;
    }

    /**
     * Connects to Chrome via CDP protocols and scraps daily activities.
     * @param profileDir The target browser profile directory name.
     */
    public void scrapeDailyActivities(String profileDir) {
        killAllChromeProcesses();
        disableProtocolHandlers(profileDir);

        launchChromeNatively(profileDir);

        System.out.println("[DEBUG] Attaching Playwright via CDP Connect...");
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().connectOverCDP(DEBUG_URL)) {
                BrowserContext context = browser.contexts().get(0);
                runScraperWorkflow(context);
            }
        } catch (Exception e) {
            System.err.println("❌ Critical runtime exception in Playwright CDP lifecycle: " + e.getMessage());
        } finally {
            killAllChromeProcesses();
        }
    }

    private void launchChromeNatively(String profileName) {
        System.out.println("[DEBUG] Launching Chrome with remote debugging via ProcessBuilder...");
        try {
            List<String> command = new ArrayList<>(List.of(
                    settings.getChromePath(),
                    "--remote-debugging-port=9222",
                    "--remote-debugging-address=127.0.0.1",
                    "--user-data-dir=" + settings.getUserDataDir(),
                    "--profile-directory=" + profileName,
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--start-maximized",
                    "--hide-crash-restore-bubble",
                    "--disable-features=Translate",
                    "--disable-blink-features=AutomationControlled",
                    "--excludeSwitches=enable-automation"
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.start();

            if (!waitForDebugPort()) {
                System.out.println("❌ Failed to detect remote debugging port after launch.");
                return;
            }
            System.out.println("✅ Remote debugging port is open and operational.");
        } catch (Exception e) {
            System.err.println("Failed to launch native Chrome: " + e.getMessage());
        }
    }

    private boolean waitForDebugPort() throws InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        for (int i = 0; i < 15; i++) {
            try {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(DEBUG_URL + "/json/version"))
                        .timeout(java.time.Duration.ofSeconds(3))
                        .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body().contains("Browser")) {
                    return true;
                }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        return false;
    }

    private void runScraperWorkflow(BrowserContext context) {
        try {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            System.out.println("Navigating to: " + TARGET_URL);
            page.navigate(TARGET_URL, new Page.NavigateOptions().setTimeout(60000));

            processActivitiesSection(context, page);

            int finalDelay = 8 + random.nextInt(6);
            System.out.printf("%n✅ All activities processed! Wrapping up smoothly in %d seconds.%n", finalDelay);
            Thread.sleep(finalDelay * 1000L);
        } catch (Exception e) {
            System.err.println("Failed during automation processing loop: " + e.getMessage());
        }
    }

    private void processActivitiesSection(BrowserContext context, Page page) {
        Locator activitySelector = page.locator("#moreactivities a[href], #moreactivities button");
        try {
            activitySelector.first().waitFor(new Locator.WaitForOptions().setTimeout(15000));
        } catch (PlaywrightException e) {
            System.out.println("❌ Timed out waiting for activities to load inside #moreactivities.");
            return;
        }

        int totalCount = activitySelector.count();
        System.out.println("Found " + totalCount + " potential items to check.");

        for (int i = 0; i < totalCount; i++) {
            if (!Main.running) break;

            try {
                Locator currentActivity = activitySelector.nth(i);
                String cleanText = currentActivity.innerText(new Locator.InnerTextOptions().setTimeout(3000))
                        .replace("\n", " ").trim();

                if (cleanText.isEmpty()) continue;

                String href = currentActivity.getAttribute("href", new Locator.GetAttributeOptions().setTimeout(3000));
                System.out.printf("→ Activity [%d/%d]: %s%n  Link: %s%n", (i + 1), totalCount, cleanText, href != null ? href : "N/A (Button)");

                if (shouldSkipActivity(cleanText)) {
                    System.out.println("-----------------------------------");
                    continue;
                }

                executeActivityTask(context, currentActivity, cleanText);
                System.out.println("-----------------------------------");
            } catch (PlaywrightException e) {
                System.out.println("ℹ️ Layout shifted or item went stale at index " + i + ". Skipping smoothly...");
            }
        }
    }

    private boolean shouldSkipActivity(String text) {
        if (text.contains("Completed")) return true;

        // Ensure item awards reward points
        return !text.contains("+5") && !text.contains("+10") && !text.contains("+15") && !text.contains("points");
    }

    private void executeActivityTask(BrowserContext context, Locator activity, String activityName) {
        try {
            String lowerName = activityName.toLowerCase();
            if (lowerName.contains("search bar") || lowerName.contains("referral") || lowerName.contains("mobile app")) {
                System.out.println("⏭️ Skipping high-risk or external protocol activity: " + activityName);
                return;
            }

            Page newTab = context.waitForPage(() -> {
                activity.click();
                try { Thread.sleep(800); } catch (Exception ignored) {}
                dismissProtocolPopup();
            });

            newTab.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10000));
            Thread.sleep(7000);
            newTab.close();
        } catch (Exception e) {
            System.err.println("Error executing activity [" + activityName + "]: " + e.getMessage());
            dismissProtocolPopup();
        }
    }

    private void dismissProtocolPopup() {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(500);
        } catch (AWTException | InterruptedException e) {
            System.err.println("AWT Robot failure dismissing browser popup: " + e.getMessage());
        }
    }

    private void disableProtocolHandlers(String profileDir) {
        if (!settings.getOs().toLowerCase().contains("win")) return;

        try {
            Path prefsFile = Paths.get(settings.getUserDataDir(), profileDir, "Preferences");
            if (!Files.exists(prefsFile)) return;

            String content = Files.readString(prefsFile);
            if (!content.contains("protocol_handler")) {
                String protocolSection = """
                ,"protocol_handler":{"excluded_schemes":{"ms-edge":true,"edge":true,"spotify":true,"xbox":true,"ms-settings":true,"tel":true,"mailto":true}}
                ,"profile":{"default_content_setting_values":{"protocol_handlers":2}}
                """;
                content = content.replaceFirst("(?s)\\s*\\}\\s*$", protocolSection + "\n}");
                Files.writeString(prefsFile, content);
                System.out.println("✅ Successfully patched Preferences to block protocol prompts");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to patch Preference preferences configurations: " + e.getMessage());
        }
    }

    private void killAllChromeProcesses() {
        try {
            if (settings.getOs().toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("taskkill /F /IM chrome.exe");
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe");
            } else {
                new ProcessBuilder("pkill", "-9", "chrome").start();
            }
            Thread.sleep(4000);
        } catch (Exception ignored) {}
    }
}