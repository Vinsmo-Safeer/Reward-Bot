package org.bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WebScrapper_v2 {

    static String os = System.getProperty("os.name").toLowerCase();

    public static void main(String chromePath, String userDataDir, String profileDir) {

        killAllChromeProcesses();  // Stronger kill


        String targetUrl = "https://rewards.bing.com/earn";   // ← Change this

        final String debugUrl = "http://127.0.0.1:9222";

        launchChrome(chromePath,userDataDir, profileDir, debugUrl);

        attachPlaywrite(debugUrl, targetUrl, userDataDir);

    }

    private static void attachPlaywrite(String debugUrl, String targetUrl, String userDataDir) {

        // === 2. Attach Playwright ===
        System.out.println("[DEBUG] Attaching Playwright via CDP...");
        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().connectOverCDP(debugUrl);  // Use 127.0.0.1
            BrowserContext context = browser.contexts().get(0);
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            System.out.println("Navigating to: " + targetUrl);
            page.navigate(targetUrl, new Page.NavigateOptions().setTimeout(60000));



            System.out.println("\n=== PAGE TITLE ===");
            System.out.println(page.title());

//            allElements(page);


            // 1. Target the specific container using the ID
            ElementHandle moreActivitiesSection = page.querySelector("#moreactivities");

            if (moreActivitiesSection != null) {
                System.out.println("\n=== KEEP EARNING (MORE ACTIVITIES) ===");

                // 2. Query only the interactive elements inside this section
                List<ElementHandle> activities = moreActivitiesSection.querySelectorAll("a[href], button");

                for (ElementHandle activity : activities) {
                    String text = activity.textContent().trim();
                    String href = activity.getAttribute("href");
                    String ariaLabel = activity.getAttribute("aria-label");

                    // Sometimes the card text is split; getting innerText can help clean it up
                    String cleanText = (String) activity.evaluate("el => el.innerText").toString().replace("\n", " ").trim();

                    if (!cleanText.isEmpty()) {
                        System.out.printf("→ Activity: %s%n  Link: %s%n",
                                cleanText,
                                href != null ? href : "N/A (Button)");
                        if (!cleanText.contains("Completed")) {
                            System.out.println("\n Not Completed this Activity");

                            try {

                                if (!cleanText.contains("Completed")) {
                                    System.out.println("Executing Activity: " + cleanText);

                                    // 1. Click the element and wait for a new page (tab) to open
                                    Page newTab = context.waitForPage(() -> {
                                        activity.click();
                                    });

                                    // 2. Wait for the new tab to actually load content
                                    newTab.waitForLoadState(
                                            LoadState.LOAD,
                                            new Page.WaitForLoadStateOptions().setTimeout(10000) // 10 seconds
                                    );

                                    // 3. Stay on the page for a few seconds to mimic reading
                                    Thread.sleep(7000);

                                    // 4. Close the tab and go back to the dashboard
                                    newTab.close();
                                }
                            }catch (Exception e) {
                                System.err.println(e);
                                System.out.println("Continuing Program...");
                            }

                        }
                        System.out.println("-----------------------------------");
                    }
                }
            } else {
                System.out.println("Section #moreactivities not found on the page.");
            }

            System.out.println("\n✅ Done! Chrome window will close after 5 seconds.");

            Thread.sleep(5000);
            browser.close();
            killAllChromeProcesses();

        } catch (Exception e) {
            System.err.println("Failed to attach Playwright:");
            e.printStackTrace();
        }
    }

    private static void allElements(Page page) {
        System.out.println("\n=== BUTTONS & LINKS ===");
        List<ElementHandle> elements = page.querySelectorAll(
                "button, a[href], [role='button'], input[type='button'], input[type='submit'], [onclick]"
        );

        for (ElementHandle el : elements) {
            String tag = (String) el.evaluate("el => el.tagName");
            String text = el.textContent().trim();
            String href = el.getAttribute("href");
            String onclick = el.getAttribute("onclick");
            String id = el.getAttribute("id");
            String aria = el.getAttribute("aria-label");

            System.out.printf("→ %s | Text: '%s' | Href: %s | OnClick: %s | ID: %s | Aria: %s%n",
                    tag, text.isEmpty() ? "(no text)" : text,
                    href != null ? href : "(none)",
                    onclick != null ? onclick : "(none)",
                    id != null ? id : "(none)",
                    aria != null ? aria : "(none)");
        }
    }


    private static void launchChrome(String chromePath, String userDataDir, String profileName, String debugUrl) {
        // === 1. Launch Chrome with remote debugging ===
        System.out.println("[DEBUG] Launching Chrome with remote debugging...");
        try {
            List<String> command = new ArrayList<>();
            command.add(chromePath);
            command.add("--remote-debugging-port=9222");
            command.add("--remote-debugging-address=0.0.0.0");  // Try to force IPv4
            command.add("--user-data-dir=" + userDataDir);
            command.add("--profile-directory=" + profileName);
            command.add("--no-first-run");
            command.add("--no-default-browser-check");
            command.add("--disable-infobars");
            command.add("--start-maximized");
            command.add("--disable-background-networking");     // helps with some startup issues
            command.add("--disable-features=Translate");       // minor
            command.add("--disable-sync");
            command.add("--no-first-run");
            command.add("--hide-crash-restore-bubble");
            command.add("--disable-external-intent-requests");

            // Optional: open target URL directly
            // command.add(targetUrl);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process chromeProcess = pb.start();

            System.out.println("✅ Chrome launched with Profile: " + profileName);
            System.out.println("Waiting for remote debugging port...");

            // Wait + check port with retries
            boolean portReady = waitForDebugPort(debugUrl, 15);  // up to ~15 seconds

            if (!portReady) {
                System.out.println("❌ Failed to detect remote debugging port after launch.");
                System.out.println("   → Try manually closing ALL Chrome windows (including background) and run again.");
                System.out.println("   → Or test manually: Open browser and go to " + debugUrl + "/json/version");
                return;
            }

            System.out.println("✅ Remote debugging port is ready!");

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private static void killAllChromeProcesses() {
        try {
            System.out.println("Killing all Chrome processes...");
            if (os.contains("win")) {
                Runtime.getRuntime().exec("taskkill /F /IM chrome.exe");
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe");
            } else {
                ProcessBuilder pb = new ProcessBuilder("pkill", "-9", "chrome");
                pb.start();
            }
            Thread.sleep(4000);  // longer wait
            System.out.println("Chrome processes killed.");
        } catch (Exception ignored) {
            System.err.println("[Warning]: Could not close Chrome instances");
        }
    }

    private static boolean waitForDebugPort(String baseUrl, int maxSeconds) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        for (int i = 0; i < maxSeconds; i++) {
            try {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(baseUrl + "/json/version"))
                        .timeout(java.time.Duration.ofSeconds(3))
                        .build();

                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body().contains("Browser")) {
                    System.out.println("Port check successful: " + response.body().substring(0, Math.min(300, response.body().length())));
                    return true;
                }
            } catch (Exception ignored) {
                // expected while Chrome is starting
            }
            Thread.sleep(1000);
            System.out.print(".");
        }
        System.out.println();
        return false;
    }
}
