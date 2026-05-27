package org.bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class WebScraper_v1 {



    public static void main(String[] args) {
        System.out.println("[DEBUG 0] Starting Rewards Bot...");

        // === CONFIGURATION ===
        String originalUserDataDir = "C:\\Users\\lenovo\\AppData\\Local\\Google\\Chrome\\User Data";
        String profileName = "Profile 3";   // or "Profile 1", etc.

        Path tempUserDataDir = Paths.get(System.getProperty("java.io.tmpdir"), "playwright_rewards_profile");

        killChromeProcesses();

        try {
            copyProfileToTemp(originalUserDataDir, profileName, tempUserDataDir);

            try (Playwright playwright = Playwright.create()) {
                BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setChannel("chrome")
                        .setArgs(List.of(
                                "--no-first-run",
                                "--disable-blink-features=AutomationControlled",
                                "--disable-features=ChromeWhatsNewUI"
                        ))
                        .setIgnoreDefaultArgs(List.of("--enable-automation"))
                        .setViewportSize(1366, 900);

                System.out.println("[DEBUG] Launching with temp profile...");
                try (BrowserContext context = playwright.chromium().launchPersistentContext(tempUserDataDir, options)) {
                    Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

                    // Better navigation with longer timeout + wait for network
                    System.out.println("[DEBUG] Navigating to Microsoft Rewards...");
                    page.navigate("https://rewards.bing.com/", new Page.NavigateOptions().setTimeout(90000));
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30000));

                    // If still on welcome page, try to go to dashboard
                    if (page.url().contains("welcome") || page.title().contains("Welcome")) {
                        System.out.println("[DEBUG] On welcome page → trying dashboard...");
                        page.navigate("https://rewards.bing.com/", new Page.NavigateOptions().setTimeout(60000));
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                    }

                    page.bringToFront();

                    System.out.println("[DEBUG] Current URL: " + page.url());
                    System.out.println("[DEBUG] Page title: " + page.title());

                    // Give the dynamic dashboard more time to render (very important)
                    page.waitForTimeout(8000);

                    // === UPDATED SELECTORS for 2026 dashboard ===
                    // These are more robust and target common offer/quest containers
                    Locator offers = page.locator("div[class*='offer'], div[class*='quest'], div[class*='card'], article, section div[role='button']");
                    int count = offers.count();

                    System.out.println("[DEBUG] Found ~" + count + " potential offer elements.");

                    boolean foundAny = false;
                    for (int i = 0; i < Math.min(count, 30); i++) {   // limit to avoid too many
                        Locator item = offers.nth(i);
                        String text = item.innerText().trim();

                        if (text.isEmpty()) continue;

                        // Skip obviously completed or locked items
                        if (text.toLowerCase().contains("completed") ||
                                text.toLowerCase().contains("done") ||
                                text.toLowerCase().contains("level required") ||
                                text.contains("0/") && text.contains(" points")) {  // rough completed pattern
                            continue;
                        }

                        // Try to get a clean title
                        String title = item.locator("h1, h2, h3, h4, .title, strong, span[class*='title']").first().innerText().trim();
                        if (title.isEmpty()) title = text.substring(0, Math.min(80, text.length()));

                        System.out.println(">>> AVAILABLE / PENDING: " + title);
                        foundAny = true;

                        // Optional: click to open if you want to automate further
                        // item.click(); page.waitForTimeout(2000);
                    }

                    if (!foundAny) {
                        System.out.println("[INFO] No pending offers detected with current selectors.");
                        System.out.println("       You may need to inspect the page manually and update selectors.");
                    }

                    System.out.println("[DEBUG] Script finished. Browser stays open for 30s...");
                    page.waitForTimeout(30000);
                }
            }
        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR]");
            e.printStackTrace();
        } finally {
            // deleteDirectory(tempUserDataDir);   // uncomment only if you want cleanup
        }
    }

    // Kill all Chrome processes
    private static void killChromeProcesses() {
        System.out.println("[DEBUG] Killing existing Chrome instances...");
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe", "/T").start();
                Thread.sleep(1500); // Give time to kill
                System.out.println("Chrome processes killed.");
            } catch (Exception e) {
                System.out.println("Taskkill failed or no Chrome running.");
            }
        }
    }

    // Copy Chrome profile to temporary directory
    private static void copyProfileToTemp(String originalUserDataDir, String profileName, Path tempDir) throws IOException {
        Path sourceProfile = Paths.get(originalUserDataDir, profileName);
        Path destProfile = tempDir.resolve(profileName);

        if (!Files.exists(sourceProfile)) {
            throw new IOException("Profile not found: " + sourceProfile);
        }

        System.out.println("[DEBUG] Copying profile from: " + sourceProfile);
        System.out.println("[DEBUG] To: " + destProfile);

        // Delete old temp folder if exists
        if (Files.exists(tempDir)) {
            deleteDirectory(tempDir);
        }

        Files.createDirectories(destProfile);

        // Copy the profile directory (recursive)
        copyDirectory(sourceProfile, destProfile);

        System.out.println("[DEBUG] Profile copied successfully to temp folder.");
    }

    // Helper: Recursive directory copy
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            Path dest = target.resolve(source.relativize(src));
            try {
                if (Files.isDirectory(src)) {
                    if (!Files.exists(dest)) Files.createDirectory(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                // Skip problematic files like Lock file, SingletonSocket, etc.
                if (!src.getFileName().toString().contains("lock") &&
                        !src.getFileName().toString().contains("Singleton")) {
                    System.err.println("Warning: Could not copy " + src + " -> " + e.getMessage());
                }
            }
        });
    }

    // Helper: Delete directory recursively
    private static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore errors during cleanup
                        }
                    });
        } catch (IOException e) {
            System.err.println("Warning: Could not fully delete temp directory: " + e.getMessage());
        }
    }
}