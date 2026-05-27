package org.bot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Scanner;

public class Main {

    static String os = System.getProperty("os.name").toLowerCase();

    static int maxSearchPerDay = 5;

    static volatile boolean running = true;

    static int numberOfRuns = 0;

    public static void main(String[] args) {

        String ProfileFile = "assets/Profiles.json";

        // Thread to listen for "stop"
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();

                if (input.toLowerCase().contains("stop")) {
                    System.out.println("Stopping after current task...");
                    running = false;
                    break;
                }
            }
        }).start();

        while (running) {
            System.out.println("\n\n------------\nRan for " + numberOfRuns + " times!\n------------\n\n");
            numberOfRuns++;

            DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

            LocalDateTime now = LocalDateTime.now();

            String formatted = now.format(format);

            System.out.println(formatted);

            String chromePath;
            String userDataDir;

            if (os.contains("win")) {
                chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
                userDataDir = "C:\\chrome-debug-Profiles";
            } else {
                chromePath = "google-chrome";
//                userDataDir = "/home/rewardsbot/Desktop/Bot/chrome_usrData";
                userDataDir = "chrome_usrData";
            }

            try {

                String fileContent = new String(Files.readAllBytes(Paths.get(ProfileFile)));
                JSONObject root = new JSONObject(fileContent);
                JSONArray profiles = root.getJSONArray("profiles");

                for (int i = 0; i < profiles.length(); i++) {

                    if (!running) {
                        System.out.println("Stopping the process...");
                        return;
                    }

                    System.out.print("\n");

                    JSONObject profile = profiles.getJSONObject(i);
                    String name = profile.getString("name");
                    String directory = profile.getString("profileDir");
                    String lastSearchTime = profile.getString("lastSearchTime");
                    int timesSearched = profile.getInt("timesSearched");

                    System.out.println("Checking Profile named '" + name + "'");
                    System.out.println("Directory name: " + directory);
                    System.out.println("Time of last search: " + lastSearchTime);


                    String[] PreviousTime = lastSearchTime.split(" ");
                    String[] currentTime = formatted.split(" ");

                    if (!Objects.equals(PreviousTime[0], currentTime[0])) {
                        timesSearched = 0;
                    }


                    System.out.println("Times searched on bing today: " + timesSearched);


                    if (timesSearched < maxSearchPerDay) {



                        System.out.println("\nRunning search bot...");
                        SearchBot_v2.main(chromePath, userDataDir, name, directory, os);
                        timesSearched++;
                        lastSearchTime = formatted;

                    }


                    // Set the timesSearched and lastSearchTime to the file
                    System.out.println("Setting the new variables");
                    profile.put("timesSearched", timesSearched);
                    profile.put("lastSearchTime", lastSearchTime);

                    System.out.println("Saving data...");
                    Path original = Paths.get(ProfileFile);
                    Path temp = Paths.get(ProfileFile + ".tmp");

                    Files.copy(original, Paths.get(ProfileFile + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                    Files.write(temp, root.toString(4).getBytes());
                    Files.move(temp, original, StandardCopyOption.REPLACE_EXISTING);

                    if (running) {
                        System.out.println("Checking if activity check is needed");
                        String lastActivityCheck = profile.getString("lastActivityCheck");



                        // 1. Extract and Parse the Times
                        LocalTime currentLocalTime = LocalTime.parse(currentTime[1]);

                        // lastActivityCheck is "<date> <time>", so we split by space and take index 1
                        String lastActivityTimeStr = lastActivityCheck.split(" ")[1];
                        LocalTime lastActivityTime = LocalTime.parse(lastActivityTimeStr);

                        // 2. Calculate the Difference
                        long minutesPassed = Math.abs(Duration.between(currentLocalTime, lastActivityTime).toMinutes());

                        System.out.println("Current: " + currentLocalTime + " | Last: " + lastActivityTime);
                        System.out.println("Minutes Difference: " + minutesPassed);


                        if (minutesPassed > 50) {
                            System.out.println("More than 50 minutes passed. Starting WebScraper...");
                            WebScrapper_v2.main(chromePath, userDataDir, directory);

                            lastActivityCheck = formatted;
                            profile.put("lastActivityCheck", lastActivityCheck);
                        } else {
                            System.out.println("Only " + minutesPassed + " minutes passed. Skipping.");
                        }

                    }



                    System.out.println("Saving data...");

                    Path original = Paths.get(ProfileFile);
                    Path temp = Paths.get(ProfileFile + ".tmp");

                    Files.copy(original, Paths.get(ProfileFile + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                    Files.write(temp, root.toString(4).getBytes());
                    Files.move(temp, original, StandardCopyOption.REPLACE_EXISTING);

                    System.out.println("\n -------------------------------");
                }


                System.out.println("Waiting 30 seconds...");
                Thread.sleep(30_000);

            } catch (Exception e) {
                System.err.println(e);
            }

        }
    }

}