package org.bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WebScraperService {

    DatabaseManager db;
    ProxyManger proxyManger;
    private final Settings settings;

    private static final String TARGET_URL = "https://rewards.bing.com";
    private static final String DEBUG_URL = "http://127.0.0.1:9222";

    private final Random random = new Random();

    public WebScraperService(Settings settings, DatabaseManager db) {
        this.settings = settings;
        this.db = db;
    }

    /**
     * Connects to Chrome via CDP protocols and scraps daily activities.
     * @param profile The target browser profile configuration object.
     */
    public String scrapeDailyActivities(Profile profile) {
        killAllChromeProcesses();
        disableProtocolHandlers(profile.getProfileDir());

        String launchChromeResult = launchChromeNatively(profile);

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

        return launchChromeResult;
    }

    private String launchChromeNatively(Profile profile) {
        System.out.println("[DEBUG] Launching Chrome with remote debugging via ProcessBuilder...");

        String proxyInfo = "";
        try {

            if (settings.isEnableProxy()) {
                proxyManger = new ProxyManger(settings);
                proxyManger.loadingProxies();
                Proxy proxy = proxyManger.getAppropriateProxy(profile.getProfileDir(), db, profile);

                if (proxy == null) {
                    System.out.println("No proxy available for profile '" + profile.getProfileDir() + "', skipping activity check.");
                    return null;
                }

                List<String> command = new ArrayList<>(List.of(
                        settings.getChromePath(),
                        "--remote-debugging-port=9222",
                        "--remote-debugging-address=127.0.0.1",
                        "--user-data-dir=" + settings.getUserDataDir(),
                        "--profile-directory=" + profile.getProfileDir(),
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--start-maximized",
                        "--hide-crash-restore-bubble",
                        "--disable-features=Translate",
                        "--disable-blink-features=AutomationControlled",
                        "--excludeSwitches=enable-automation",

                        "--proxy-server=http://" + proxy.getProxyAddress() + ":" + proxy.getPort()
                ));

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                pb.start();

                    proxyInfo = proxy.getProxyAddress() + "_" + proxy.getCountryCode();


            }else {

                List<String> command = new ArrayList<>(List.of(
                        settings.getChromePath(),
                        "--remote-debugging-port=9222",
                        "--remote-debugging-address=127.0.0.1",
                        "--user-data-dir=" + settings.getUserDataDir(),
                        "--profile-directory=" + profile.getProfileDir(),
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
            }





            if (!waitForDebugPort()) {
                System.out.println("❌ Failed to detect remote debugging port after launch.");
                return null;
            }
            System.out.println("✅ Remote debugging port is open and operational.");
        } catch (Exception e) {
            System.err.println("Failed to launch native Chrome: " + e.getMessage());
        }

        return "0 " + proxyInfo;
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
            System.out.println("Navigating to: " + TARGET_URL); // Target URL = https://rewards.bing.com
            page.navigate(TARGET_URL, new Page.NavigateOptions().setTimeout(60000));

            // Give the core document shell a brief moment to render before scanning elements
            page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(20000));

            // Dynamic layout fingerprint checks based on your screenshots
            // 1. Legacy checks for the Angular custom dashboard tags
            boolean isLegacyAngularLayout = page.locator("mee-rewards-dashboard").count() > 0;

            // 2. Modern checks for the new React layout base shell div layout (from image_874aa1.jpg)
            boolean isModernReactLayout = page.locator("div#shell").count() > 0 || page.locator("div.flex.min-h-screen").count() > 0;

            if (isLegacyAngularLayout) {
                System.out.println("[INFO] loading, North America-Euro legacy version...");

                scrapeAndProcessAllMeeCards(page, 20000, context);


            } else if (isModernReactLayout) {
                System.out.println("[INFO] loading, Asia-Global expansion version...");

                // Change page to the /earn sub-page because the modern layout keeps its tiles there
                System.out.println("Redirecting modern dashboard to earn page view...");
                page.navigate("https://rewards.bing.com/earn", new Page.NavigateOptions().setTimeout(30000));

                // Wait for the target activity container to load securely before running your logic
                page.waitForSelector("#moreactivities", new Page.WaitForSelectorOptions().setTimeout(15000));

                // Routes cleanly to your existing, flawless automation logic
                processActivitiesSection(context, page);

            } else {
                // Fallback catch-all in case Microsoft is running an unmapped structural variation
                System.out.println("[WARNING] Unknown base layout version. Defaulting to modern workflow routing via /earn...");
                page.navigate("https://rewards.bing.com/earn", new Page.NavigateOptions().setTimeout(30000));
                processActivitiesSection(context, page);
            }

            int finalDelay = 8 + random.nextInt(6);
            System.out.printf("%n✅ All activities processed! Wrapping up smoothly in %d seconds.%n", finalDelay);
            Thread.sleep(finalDelay * 1000L);
        } catch (Exception e) {
            System.err.println("Failed during automation processing loop: " + e.getMessage());
        }
    }


    public void waitFor(long minMillis, long maxMillis) throws InterruptedException {
        long delay = minMillis + (long) (random.nextDouble() * (maxMillis - minMillis));
        Thread.sleep(delay);
    }



    public void scrapeAndProcessAllMeeCards(Page page, int timeoutMillis, BrowserContext context) throws InterruptedException {

        // Wait for 4 - 8 seconds to allow the page to load and render the cards
        waitFor(4000, 8000);

        System.out.println("Waiting for mee-cards to render on the page...");
        try {
            // 1. Wait for at least one card to exist in the DOM
            page.waitForSelector("mee-card", new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMillis));

            // 2. Fetch all matching mee-card elements into a collection
            Locator allCards = page.locator("mee-card");
            int totalCardsCount = allCards.count();
            System.out.println("Detected a total of " + totalCardsCount + " cards on the dashboard.");

            // 3. Sequential Loop over each card index
            for (int i = 0; i < totalCardsCount; i++) {

                waitFor(500, 1000);

                System.out.println("\n=================================");
                System.out.println("PROCESSING CARD " + (i + 1) + " OF " + totalCardsCount);
                System.out.println("=================================");

                // --- CRITICAL FIX 1: ENCAPSULATE EACH CARD IN ITS OWN TRY/CATCH BLOCK ---
                try {
                    // Target the specific card at index 'i'
                    Locator card = allCards.nth(i);

                    // Skip hidden background/history elements right away
                    if (!card.isVisible()) {
                        System.out.println("Skipping card: Element is hidden in background DOM (History Card).");
                        continue;
                    }

                    // Identify Category by Parent Context
                    String category = "Unknown Category";
                    if (card.locator("xpath=ancestor::div[@id='daily-sets']").count() > 0) {
                        category = "Daily Set";
                    } else if (card.locator("xpath=ancestor::div[@id='explore-on-bing']").count() > 0) {
                        category = "Explore on Bing";
                    } else if (card.locator("xpath=ancestor::div[@id='more-activities']").count() > 0) {
                        category = "More Activities";
                    }

                    System.out.println("Category: 📂 " + category);

                    // Focus on the inner data container wrapper to pull variables
                    Locator dataContainer = card.locator(".rewards-card-container");

                    String title = "Unknown Title";
                    String description = "Unknown Description";
                    String points = "Unknown Points";

                    if (dataContainer.isVisible()) {
                        String ariaLabel = dataContainer.getAttribute("aria-label");
                        if (ariaLabel != null && !ariaLabel.isEmpty()) {
                            String[] details = ariaLabel.split(",");
                            if (details.length >= 3) {
                                title = details[0].trim();
                                description = details[1].trim();
                                points = details[2].trim();
                            }
                        }
                    }

                    System.out.println("Title: " + title);
                    System.out.println("Description: " + description);
                    System.out.println("Points: " + points);

                    // =================================================================
// 4. VERIFY COMPLETION STATUS (CATEGORIZED LADDER ENGINE)
// =================================================================
                    boolean isCompleted = false;
                    boolean isLocked = false;

                    String rawHtml = card.innerHTML();

                    if ("Explore on Bing".equals(category)) {
                        // Keep your original working logic completely untouched for this section
                        Locator checkMarkElement = card.locator(".mee-icon-CheckMark, .icon-check");
                        isCompleted = checkMarkElement.count() > 0 && checkMarkElement.first().isVisible();

                        isLocked = rawHtml.contains("locked-card")
                                || rawHtml.contains("DayOfWeek_Dashboard_Lock.svg")
                                || rawHtml.contains("exclusiveLockedFeatureCardStatus == 'locked'");

                        boolean isUnlockedAndReady = rawHtml.contains("dashboard_unlocked.svg")
                                || rawHtml.contains("unlocked_tooltip");

                        if (isLocked && isUnlockedAndReady) {
                            isLocked = false;
                        }

                    } else if ("Daily Set".equals(category)) {
                        // Daily Set completion uses a unique overlay state structure.
                        // When completed, it embeds a custom completion validation icon image inside the item content block.
                        Locator dailyCheckMark = card.locator("mee-rewards-daily-set-item-content .c-image");

                        // Fallback: check if the card template elements explicitly drop the incomplete templates
                        boolean hasCompletedIcon = dailyCheckMark.count() > 0 && dailyCheckMark.first().isVisible();
                        boolean missingIncompleteTemplates = !rawHtml.contains("!$ctrl.item.complete");

                        isCompleted = hasCompletedIcon || missingIncompleteTemplates;

                        // Daily Set uses its own locked-card assets
                        isLocked = rawHtml.contains("locked-card")
                                || rawHtml.contains("DayOfWeek_Dashboard_Lock.svg");

                        boolean isUnlockedAndReady = rawHtml.contains("dashboard_unlocked.svg")
                                || rawHtml.contains("unlocked_tooltip");

                        if (isLocked && isUnlockedAndReady) {
                            isLocked = false;
                        }

                    } else if ("More Activities".equals(category)) {
                        // Locks do not exist in this section
                        isLocked = false;

                        // More Activities updates the accessibility label to contain the word "earned"
                        if (dataContainer.isVisible()) {
                            String ariaLabel = dataContainer.getAttribute("aria-label");
                            if (ariaLabel != null) {
                                String ariaLower = ariaLabel.toLowerCase();
                                if (ariaLower.contains("earned") || ariaLower.contains("points points")) {
                                    isCompleted = true;
                                }
                            }
                        }

                        // If the inner text references the finalized value check layout directly
                        if (!isCompleted) {
                            isCompleted = rawHtml.contains("points earned") || rawHtml.contains("completed");
                        }
                    }

                    System.out.println("Points Value: " + points);

// =================================================================
// 5. DECISION AND EXECUTION LADDER
// =================================================================
                    if (isCompleted && !description.contains("Unknown")) {
                        System.out.println("Status: ✅ Already Completed (Skipping)");
                    } else if (isLocked && !description.contains("Unknown")) {
                        System.out.println("Status: 🔒 Locked by Timer (Skipping)");
                    } else {
                        // Extra validation layout filter for the More Activities category
                        if ("More Activities".equals(category)) {
                            String pointsLower = points.toLowerCase();
                            boolean isValidEarningCard = pointsLower.contains("earn") && pointsLower.matches(".*\\d+.*");

                            if (!isValidEarningCard) {
                                System.out.println("Status: ⏭️ Non-earning promotional card detected ('" + points + "'). Skipping click.");
                                continue;
                            }
                        }

                        System.out.println("Status: ❌ Incomplete / Unlocked (Processing...)");

                        waitFor(2000, 5000);

                        System.out.println("Initiating navigation click...");
                        Locator linkClickTarget = card.locator("a.ds-card-sec");

                        Page newTab = null;
                        try {
                            // CORRECT JAVA SYNTAX: waitForPopup belongs to 'page', and options are the second argument!
                            newTab = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(6000), () -> {
                                if (linkClickTarget.count() > 0 && linkClickTarget.first().isVisible()) {
                                    linkClickTarget.first().click();
                                } else {
                                    card.click();
                                }
                            });
                            System.out.println("Successfully clicked card and intercepted the new tab natively!");
                        } catch (Exception popupEx) {
                            System.out.println("Note: No external tab popup opened within 6s. Handled inline or skipped.");
                        }

                        // Wait for network states safely
                        if (newTab != null) {
                            try {
                                newTab.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                            } catch (Exception loadEx) {
                                System.out.println("Note: New tab network didn't go completely idle, proceeding anyway.");
                            }

                            // CRITICAL FIX FOR 2-TAB ROBOT BUG: Ensure OS window focus is on the new tab before typing
                            newTab.bringToFront();
                            Thread.sleep(1500);
                        } else {
                            try {
                                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                            } catch (Exception loadEx) {}
                        }

                        // If an auxiliary tab exists, execute your typing search
                        if (context.pages().size() > 1) {
                            System.out.println("Detected an auxiliary promotional tab session.");
                            searchOnBing(description);
                        }

                        waitFor(4000, 6000);

                        // Housekeeping: Clean up other tabs safely
                        for (Page singlePage : context.pages()) {
                            if (singlePage != page) {
                                try {
                                    singlePage.close();
                                } catch (Exception closeEx) {
                                    // Ignore tab close issues
                                }
                            }
                        }
                    }
                } catch (Exception cardException) {
                    // --- CRITICAL FIX 2: CATCH BLOCK PREVENTS LOOP CRASHES ---
                    System.err.println("⚠️ ERROR ON CARD " + (i + 1) + ": " + cardException.getMessage());
                    System.err.println("Skipping this card and moving forward with the remaining queue...");

                    // Emergency tab cleanup just in case a broken card left an orphaned tab open
                    for (Page singlePage : context.pages()) {
                        if (singlePage != page) {
                            try { singlePage.close(); } catch (Exception e) {}
                        }
                    }
                }
            }
            System.out.println("\n🎉 Finished parsing all available dashboard cards successfully!");

        } catch (PlaywrightException e) {
            System.err.println("Critical structural failure on dashboard container components.");
        }
    }

    public void searchOnBing(String query) {
        // remove Search on Bing from the query if it exists
        if (query.toLowerCase().startsWith("search on bing")) {
            query = query.substring("search on bing".length()).trim();
        } else {
            return;
        }

        // remove the next word also
        String[] words = query.split(" ", 2);
        if (words.length > 1) {
            query = words[1].trim();
        }

        query = shiftToFirstPerson(query);

        // --- Robot Implementation Starts Here ---
        try {

            waitFor(2000, 4000); // Simulate human delay before starting to type

            Robot robot = new Robot();
            Random random = new Random();

            // Human typing speed constraints (in milliseconds)
            int minMillis = 100;  // Fast typing gap
            int maxMillis = 250;  // Slow typing gap / thinking gap

            for (int i = 0; i < query.length(); i++) {

                waitFor(100, 300);

                char c = query.charAt(i);

                // Get the appropriate key code (handles uppercase/lowercase automatically)
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);

                if (keyCode == KeyEvent.VK_UNDEFINED) {
                    // Skip characters that Java's Robot can't map cleanly
                    continue;
                }

                // Check if we need to hold Shift (for capital letters or symbols)
                boolean isUppercase = Character.isUpperCase(c);
                if (isUppercase) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                }

                // 1. Press the key
                robot.keyPress(keyCode);

                // 2. Random timer between press and release (Hold Duration)
                // Typically 50ms to 100ms to prevent duplicate letters/accidental double-taps
                int holdDelay = random.nextInt(50) + 50;
                Thread.sleep(holdDelay);

                // 3. Release the key
                robot.keyRelease(keyCode);
                if (isUppercase) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }

                // 4. Random timer between this letter and the next (Flight Time)
                // Only sleep if it's not the very last character
                if (i < query.length() - 1) {
                    int keyToKeyDelay = random.nextInt((maxMillis - minMillis) + 1) + minMillis;
                    Thread.sleep(keyToKeyDelay);
                }
            }

            waitFor(3000, 6000);

            // 6. Press Enter
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

        } catch (AWTException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String shiftToFirstPerson(String input) {
        if (input == null) {
            return null;
        }

        // \\b represents a word boundary.
        // This ensures we only replace the exact word "your".
        // (?i) makes it case-insensitive so it catches "Your" or "your".
        return input.replaceAll("(?i)\\byour\\b", "my");
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