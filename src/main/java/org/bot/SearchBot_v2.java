package org.bot;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class SearchBot_v2 {

    static String os;

    public static void main(String chromePath, String userDataDir, String name, String profileDir, String os) {

        SearchBot_v2.os = os;

        String url = generateBingSearchUrl();

        System.out.println("Starting chrome...");
        try {
            new ProcessBuilder(
                    chromePath,
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + userDataDir,
                    "--profile-directory=" + profileDir,
                    "--no-first-run",
                    "--no-default-browser-check",

                    // 👇 ADD THESE TO HIDE AUTOMATION NATIVELY 👇
                    "--disable-blink-features=AutomationControlled",
                    "--excludeSwitches=enable-automation",

                    url
            ).start();

            System.out.println("Waiting for some seconds...");
            // Wait sequentially (No separate thread)
            Random random = new Random();
            int delaySeconds = 10 + random.nextInt(5);
            System.out.println("Waiting " + delaySeconds + " seconds...");
            Thread.sleep(delaySeconds * 1000);

            // Nuke all Chrome instances
            System.out.println("Trying to nuke chrome...");
            nukeChrome();
            System.out.println("Nuked Chrome");
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void nukeChrome() {

        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: /F is force, /IM is image name, /T kills child processes
                pb = new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe", "/T");
            } else {
                // Linux/Ubuntu: -9 is SIGKILL (nuke), -f matches the full command line
                pb = new ProcessBuilder("pkill", "-9", "chrome");
            }
            pb.start().waitFor();
        } catch (Exception e) {
            System.err.println("Could not nuke Chrome: " + e.getMessage());
        }
    }

    public static String generateBingSearchUrl() {

        System.out.println("Generating Search url...");

        StringBuilder url = null;

        String Query = getRandomQuery();

        String bingSearch = "https://www.bing.com/search?";
        String q = percentEncode(Query); //q=: The Query. This is the actual text you searched for.
        String form = "QBRE"; //form=QBLH: The Form Code. This identifies which part of the Bing interface you used. QBLH typically refers to the Bing homepage search box.
        String qs= "AS"; //qs=AS: Stands for "Auto-Suggest," signaling the search came from a standard interface.

        /*Example URL:
        https://www.bing.com/search?q=weather+today&form=QBRE&qs=AS*/

        url = new StringBuilder(bingSearch);
        url.append("q=").append(q).append("&form=").append(form).append("&qs=").append(qs);

        System.out.println("URL: " + url);

        return url.toString();
    }

    public static String getRandomQuery() {

        String Query = null;

        try {
            // Read all lines
            List<String> lines = Files.readAllLines(Paths.get("assets/Queries.txt"));

            // Get total lines
            int totalLines = lines.size();
//            System.out.println("Total lines: " + totalLines);

            if (totalLines != 0) {
                // Pick random line
                Random random = new Random();
                int randomIndex = random.nextInt(totalLines); // 0 to totalLines-1

                Query = lines.get(randomIndex);
            } else {
                System.out.println("File is empty.");
            }



        } catch (IOException e) {
            e.printStackTrace();
        }

        if (Query == null) {
            System.err.println("Unable to get Query");
        } else {
            System.out.println("Query: " + Query);
        }

        return Query;
    }

    public static String percentEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
