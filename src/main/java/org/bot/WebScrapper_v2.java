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

import static org.bot.Main.running; //

public class WebScrapper_v2 { //

    static String os = System.getProperty("os.name").toLowerCase(); //

    public static void main(String chromePath, String userDataDir, String profileDir) { //
        // 1. Clear out hanging processes to prevent profile locks
        killAllChromeProcesses();

        // 2. Patch protocol handlers if necessary (Optimized for Windows safety)
        disableProtocolHandlers(userDataDir, profileDir); //

        String targetUrl = "https://rewards.bing.com/earn"; //
        String debugUrl = "http://127.0.0.1:9222"; //

        // 3. Launch Chrome via native ProcessBuilder first
        launchChromeNatively(chromePath, userDataDir, profileDir, debugUrl); //

        // 4. Attach Playwright via CDP to the already running Chrome instance
        System.out.println("[DEBUG] Attaching Playwright via CDP Connect...");
        try (Playwright playwright = Playwright.create()) { //

            // Connect to the local debugging port instead of launching a native persistent context
            try (Browser browser = playwright.chromium().connectOverCDP(debugUrl)) { //
                BrowserContext context = browser.contexts().get(0); //

                // Run your clean workflow logic
                runScraperWorkflow(context, targetUrl);
            }

        } catch (Exception e) { //
            System.err.println("❌ Critical runtime exception in Playwright CDP lifecycle:");
            e.printStackTrace(); //
        }

        // Clean up when everything finishes
        killAllChromeProcesses();
    }

    /**
     * Reverts to the stable v0.5 method of launching Chrome via native OS command execution.
     */
    private static void launchChromeNatively(String chromePath, String userDataDir, String profileName, String debugUrl) { //
        System.out.println("[DEBUG] Launching Chrome with remote debugging via ProcessBuilder..."); //
        try { //
            List<String> command = new ArrayList<>(); //
            command.add(chromePath); //
            command.add("--remote-debugging-port=9222"); //
            command.add("--remote-debugging-address=127.0.0.1"); // Bind strictly to localhost
            command.add("--user-data-dir=" + userDataDir); //
            command.add("--profile-directory=" + profileName); //
            command.add("--no-first-run"); //
            command.add("--no-default-browser-check"); //
            command.add("--start-maximized"); //
            command.add("--hide-crash-restore-bubble"); //
            command.add("--disable-features=Translate"); //

            // 👇 STEALTH FLAGS TO REPLICATE PLAYWRIGHT SETTINGS NATIVELY 👇
            command.add("--disable-blink-features=AutomationControlled"); //
            command.add("--excludeSwitches=enable-automation"); //

            ProcessBuilder pb = new ProcessBuilder(command); //
            pb.redirectErrorStream(true); //
            pb.start(); //

            System.out.println("✅ Chrome launched with Profile: " + profileName); //
            System.out.println("Waiting for remote debugging port..."); //

            // Wait + check port availability
            boolean portReady = waitForDebugPort(debugUrl, 15); //

            if (!portReady) { //
                System.out.println("❌ Failed to detect remote debugging port after launch."); //
                System.out.println("   → Try manually closing ALL Chrome windows and run again."); //
                return; //
            }

            System.out.println("✅ Remote debugging port is ready!"); //
        } catch (Exception e) { //
            e.printStackTrace(); //
        }
    }

    private static boolean waitForDebugPort(String baseUrl, int maxSeconds) throws Exception { //
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder() //
                .connectTimeout(java.time.Duration.ofSeconds(5)) //
                .build(); //
        for (int i = 0; i < maxSeconds; i++) { //
            try { //
                var request = java.net.http.HttpRequest.newBuilder() //
                        .uri(java.net.URI.create(baseUrl + "/json/version")) //
                        .timeout(java.time.Duration.ofSeconds(3)) //
                        .build(); //
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()); //

                if (response.statusCode() == 200 && response.body().contains("Browser")) { //
                    return true; //
                }
            } catch (Exception ignored) {} //
            Thread.sleep(1000); //
        }
        return false; //
    }

    private static void runScraperWorkflow(BrowserContext context, String targetUrl) { //
        try { //
            Page page = getOrCreatePage(context); //
            System.out.println("Navigating to: " + targetUrl); //
            page.navigate(targetUrl, new Page.NavigateOptions().setTimeout(60000)); //
            System.out.println("\n=== PAGE TITLE ===\n" + page.title()); //

            processActivitiesSection(context, page); //

            Random random = new Random();
            int finalDelay = 8 + random.nextInt(6); // Safe random delay between 8 and 14 seconds
            System.out.println("\n✅ All activities processed! Chrome session wrapping up smoothly in " + finalDelay + " seconds."); //
            Thread.sleep(finalDelay * 1000); //

        } catch (Exception e) { //
            System.err.println("Failed during automation processing loop:"); //
            e.printStackTrace(); //
        }
    }

    private static Page getOrCreatePage(BrowserContext context) { //
        return context.pages().isEmpty() ? context.newPage() : context.pages().get(0); //
    }

    private static void processActivitiesSection(BrowserContext context, Page page) { //
        System.out.println("Waiting for #moreactivities container to be visible..."); //
        Locator activitySelector = page.locator("#moreactivities a[href], #moreactivities button"); //

        try { //
            activitySelector.first().waitFor(new Locator.WaitForOptions().setTimeout(15000)); //
        } catch (PlaywrightException e) { //
            System.out.println("❌ Timed out waiting for activities to load inside #moreactivities."); //
            return; //
        }

        System.out.println("\n=== KEEP EARNING (MORE ACTIVITIES) ==="); //
        int totalCount = activitySelector.count();
        System.out.println("Found " + totalCount + " potential items to check."); //

        // Loop through item indexes safely using dynamic resolution
        for (int i = 0; i < totalCount; i++) { //
            if (!running) break; //

            try {
                // Re-evaluate the locator dynamically at index i to prevent stale element crashes
                Locator currentActivity = activitySelector.nth(i);

                // Set a minimal timeout for retrieving inner text so it won't hang for 30s if an element disappears
                String cleanText = currentActivity.innerText(new Locator.InnerTextOptions().setTimeout(3000))
                        .replace("\n", " ").trim();

                if (cleanText.isEmpty()) continue;

                String href = currentActivity.getAttribute("href", new Locator.GetAttributeOptions().setTimeout(3000)); //
                System.out.printf("→ Activity [%d/%d]: %s%n  Link: %s%n", (i + 1), totalCount, cleanText, href != null ? href : "N/A (Button)"); //

                if (cleanText.contains("Completed") || cleanText.contains("+5") == false && cleanText.contains("+10") == false && cleanText.contains("+15") == false && cleanText.contains("points") == false) { //
                    System.out.println("-----------------------------------"); //
                    continue; //
                }

                System.out.println("\n Not Completed this Activity"); //
                executeActivityTask(context, currentActivity, cleanText); //
                System.out.println("-----------------------------------"); //

            } catch (PlaywrightException e) {
                System.out.println("ℹ️ Layout shifted or item went stale at index " + i + ". Skipping smoothly...");
                System.out.println("-----------------------------------");
            }
        }
    }

    private static void executeActivityTask(BrowserContext context, Locator activity, String activityName) { //
        try { //
            System.out.println("Executing Activity: " + activityName); //
            String lowerName = activityName.toLowerCase();
            if (lowerName.contains("search bar") || lowerName.contains("referral") || lowerName.contains("mobile app")) { //
                System.out.println("⏭️ Skipping high-risk or external protocol activity: " + activityName); //
                return; //
            }

            Page newTab = context.waitForPage(() -> { //
                activity.click(); //
                try { Thread.sleep(800); } catch (Exception ignored) {} //
                dismissProtocolPopup(); //
            }); //

            newTab.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10000)); //
            Thread.sleep(7000); //
            newTab.close(); //
        } catch (Exception e) { //
            System.err.println("Error executing activity [" + activityName + "]: " + e.getMessage()); //
            dismissProtocolPopup(); //
            System.out.println("Continuing Program..."); //
        }
    }

    private static void dismissProtocolPopup() { //
        try { //
            Robot robot = new Robot(); //
            robot.setAutoDelay(50); //
            robot.keyPress(KeyEvent.VK_ENTER); //
            robot.keyRelease(KeyEvent.VK_ENTER); //
            System.out.println("✅ Robot attempted to dismiss protocol popup (Enter key)"); //
            Thread.sleep(500); //
        } catch (AWTException | InterruptedException e) { //
            System.err.println("Robot failed: " + e.getMessage()); //
        }
    }

    private static void disableProtocolHandlers(String userDataDir, String profileDir) { //
        if (!os.contains("win")) {
            System.out.println("ℹ️ Skipping Preferences patch (Non-Windows environment)");
            return;
        }
        try { //
            Path profilePath = Paths.get(userDataDir, profileDir); //
            Path prefsFile = profilePath.resolve("Preferences"); //

            if (!Files.exists(prefsFile)) return; //

            String content = Files.readString(prefsFile); //

            if (!content.contains("protocol_handler")) { //
                String protocolSection = """
                ,"protocol_handler":{"excluded_schemes":{"ms-edge":true,"edge":true,"spotify":true,"xbox":true,"ms-settings":true,"tel":true,"mailto":true}}
                ,"profile":{"default_content_setting_values":{"protocol_handlers":2}}
                """; //
                content = content.replaceFirst("(?s)\\s*\\}\\s*$", protocolSection + "\n}"); //
                Files.writeString(prefsFile, content); //
                System.out.println("✅ Successfully patched Preferences to block protocol prompts"); //
            }
        } catch (Exception e) { //
            System.err.println("❌ Failed to patch Preferences: " + e.getMessage()); //
        }
    }

    private static void killAllChromeProcesses() { //
        try { //
            System.out.println("Killing all Chrome processes..."); //
            if (os.contains("win")) { //
                Runtime.getRuntime().exec("taskkill /F /IM chrome.exe"); //
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe"); //
            } else { //
                ProcessBuilder pb = new ProcessBuilder("pkill", "-9", "chrome"); //
                pb.start(); //
            }
            Thread.sleep(4000); //
            System.out.println("Chrome processes killed."); //
        } catch (Exception ignored) {} //
    }
}