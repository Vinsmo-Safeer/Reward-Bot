package org.bot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Main {

    static String os = System.getProperty("os.name").toLowerCase();

    static int maxSearchPerDay = 5;

    static volatile boolean running = true;

    static int numberOfRuns = 0;

    static ProfileDatabase db;
    static List<Profile> profileList;

    public static void main(String[] args) {



        db = new ProfileDatabase();
        db.createTable();

        profileList = db.loadAllProfiles();
        List<Profile> jsonProfiles = getProfileListFromJSON();

        profileList = (!jsonProfiles.isEmpty()) ? jsonProfiles : profileList;

        try {
            // Enable H2 Console
            org.h2.tools.Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
            System.out.println("✅ H2 Console started! Open browser → http://localhost:8082 For debug");
        } catch (SQLException e) {
            e.printStackTrace();
        }

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
                chromePath = "/usr/bin/google-chrome";
                userDataDir = "/home/safeer/Desktop/Bot/chrome_usrData";
            }

            try {

                if (numberOfRuns > 2) profileList = db.loadAllProfiles();

                for (int i = 0; i < profileList.size(); i++) {

                    waitForInternet();

                    if (!running) {
                        System.out.println("Stopping the process...");
                        break;
                    }

                    System.out.print("\n");

                    Profile profile = profileList.get(i);
                    String name = profile.getName();
                    String directory = profile.getProfileDir();
                    String lastSearchTime = profile.getLastSearchTime();
                    int timesSearched = profile.getTimesSearched();

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
                    profile.setTimesSearched(timesSearched);
                    profile.setLastSearchTime(lastSearchTime);

                    if (running) {
                        System.out.println("Checking if activity check is needed");
                        String lastActivityCheck = profile.getLastActivityCheck();

                        waitForInternet();

                        // 1. Extract and Parse the Times
                        LocalTime currentLocalTime = LocalTime.parse(currentTime[1]);

                        // lastActivityCheck is "<date> <time>", so we split by space and take index 1
                        String lastActivityTimeStr = lastActivityCheck.split(" ")[1];
                        LocalTime lastActivityTime = LocalTime.parse(lastActivityTimeStr);

                        // 2. Calculate the Difference
                        long minutesPassed = Math.abs(Duration.between(currentLocalTime, lastActivityTime).toMinutes());

                        System.out.println("Current: " + currentLocalTime + " | Last: " + lastActivityTime);
                        System.out.println("Minutes Difference: " + minutesPassed);


                        if (minutesPassed > 180) {
                            System.out.println("More than 180 minutes passed. Starting WebScraper...");
                            WebScrapper_v2.main(chromePath, userDataDir, directory);

                            lastActivityCheck = formatted;
                            profile.setLastActivityCheck(lastActivityCheck);
                        } else {
                            System.out.println("Only " + minutesPassed + " minutes passed. Skipping.");
                        }

                    }



                    System.out.println("Saving data...");

                    db.saveAllProfiles(profileList);

                    System.out.println("\n -------------------------------");
                }



                int remainingRuns = numberOfRuns;
                while ((remainingRuns - 900) > 0) {
                    remainingRuns = remainingRuns - 900;
                }

                long totalSecondsToWait = (long) remainingRuns * 30;

// Dynamic countdown loop
                while (running && totalSecondsToWait > 0) {

                    // 1. Calculate hours, minutes, seconds for the CURRENT remaining time
                    long hours = TimeUnit.SECONDS.toHours(totalSecondsToWait);
                    long minutes = TimeUnit.SECONDS.toMinutes(totalSecondsToWait) % 60;
                    long seconds = totalSecondsToWait % 60;

                    // 2. Overwrite the current terminal line using \r
                    System.out.print(String.format("\rWaiting for %d h %d m %d s", hours, minutes, seconds));
                    System.out.flush(); // Forces the terminal to output the text immediately

                    // 3. Wait for 5 seconds (broken down into 100ms intervals to keep 'running' responsive)
                    long segmentSeconds = Math.min(5, totalSecondsToWait);
                    long millisToWaitForThisSegment = segmentSeconds * 1000;
                    long timeElapsedMillis = 0;

                    while (running && timeElapsedMillis < millisToWaitForThisSegment) {
                        try {
                            Thread.sleep(100); // Check 'running' flag every 100ms
                            timeElapsedMillis += 100;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            running = false; // Break out of the outer loop as well
                            break;
                        }
                    }

                    // Deduct the elapsed 5 seconds (or whatever remained) from the total countdown
                    totalSecondsToWait -= segmentSeconds;
                }

                // Move to a new line once the waiting is completely finished or stopped
                System.out.println();
                System.out.println("Timer has ended");

                if (!running) System.exit(0);

            } catch (Exception e) {
                System.err.println(e);
            }

        }
    }

    public static List<Profile> getProfileListFromJSON() {

        List<Profile> profiles = new ArrayList<>();

        System.out.println("Database Loaded!");
        System.out.println("If you want to load another Data from Profiles.json Enter [yes/y] before the program starts.");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // We use a Future to run the input check asynchronously
        Future<String> inputFuture = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (reader.ready()) { // Checks if the user typed something and hit enter
                    return reader.readLine();
                }
                Thread.sleep(100); // Prevent CPU thrashing
            }
            return null;
        });

        String input = null;
        // 10-second countdown loop
        for (int i = 10; i > 0; i--) {
            System.out.print("\rProgram will start in " + i + " seconds... ");
            System.out.flush();

            // Check if the user entered anything in the last second
            if (inputFuture.isDone()) {
                try {
                    input = inputFuture.get();
                    break; // Input received, break the countdown early!
                } catch (Exception e) {
                    // Handle potential execution exceptions
                }
            }

            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Clean up the executor service
        executor.shutdownNow();
        System.out.println("\n--- Starting Program ---");

        // Evaluate the input if any was provided
        if (input != null) {
            String cleanInput = input.trim().toLowerCase();
            if (cleanInput.equals("yes") || cleanInput.equals("y")) {
                System.out.println("Reloading profiles...");
                profiles = db.loadFromJson("assets/Profiles.json");
            }
        }

        return profiles;
    }

    /**
     * Periodically checks for internet connectivity and blocks execution
     * until a connection is successfully established.
     */
    public static void waitForInternet() {
        int retryDelaySeconds = 5;

        System.out.println("Checking internet connection...");

        while (running) {
            if (isInternetAvailable()) {
                System.out.println("\nInternet connection detected!");
                break;
            }

            // Countdown loop
            for (int i = retryDelaySeconds; i > 0; i--) {
                if (!running) {
                    break;
                }
                // \r overwrites the current line
                System.out.print("\rNo internet. Retrying in " + i + " seconds...");

                try {
                    Thread.sleep(1000); // Sleep for 1 second at a time
                } catch (InterruptedException e) {
                    System.err.println("\nThe retry wait was interrupted.");
                    Thread.currentThread().interrupt();
                    return; // Exit the method cleanly if interrupted
                }
            }

            // Final message before the next immediate check
            System.out.print("\rNo internet. Retrying...          ");
            try {
                Thread.sleep(500); // Brief pause so the user can read "Retrying..."
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Attempts to open a socket connection to Google's public DNS.
     * @return true if connection succeeds, false otherwise.
     */
    public static boolean isInternetAvailable() {
        // Use a well-known, highly reliable IP address (Google DNS) and port 53 (DNS port)
        String host = "8.8.8.8";
        int port = 53;
        int timeoutMs = 3000; // Give up after 3 seconds

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true; // Connection successful
        } catch (IOException e) {
            return false; // Connection failed (no internet or host unreachable)
        }
    }

}