package org.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Main {

    static String os = System.getProperty("os.name").toLowerCase();
    static Settings settings;
    static List<Profile> profileList;
    static volatile boolean running = true;
    static int numberOfRuns = 0;

    static DatabaseService db;

    public static void main(String[] args) {
        loadSettings();
        loadDatabase();
        listenForStopCommand();

        ProfileProcessor processor = new ProfileProcessor(db, settings);

        while (running) {
            System.out.println("\n\n------------\nRan for " + numberOfRuns + " times!\n------------\n\n");
            numberOfRuns++;

            try {
                // Refresh profile references from DB on subsequent cycles
                if (numberOfRuns > 2) {
                    profileList = db.loadAllProfiles();
                }

                // Delegate parsing & execution logic out of Main
                processor.processAll(profileList);

                // Main cycle throttle cooldown
                long remainingRuns = numberOfRuns;
                while ((remainingRuns - 500) > 0) {
                    remainingRuns -= 500;
                }
                waitFor(remainingRuns * 30);

                if (!running) {
                    System.exit(0);
                }

            } catch (Exception e) {
                System.err.println("Exception encountered in execution loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void waitFor(long totalSecondsToWait) {
        while (running && totalSecondsToWait > 0) {
            long hours = TimeUnit.SECONDS.toHours(totalSecondsToWait);
            long minutes = TimeUnit.SECONDS.toMinutes(totalSecondsToWait) % 60;
            long seconds = totalSecondsToWait % 60;

            System.out.format("\rWaiting for %d h %d m %d s", hours, minutes, seconds);
            System.out.flush();

            long segmentSeconds = Math.min(5, totalSecondsToWait);
            long millisToWaitForThisSegment = segmentSeconds * 1000;
            long timeElapsedMillis = 0;

            while (running && timeElapsedMillis < millisToWaitForThisSegment) {
                try {
                    Thread.sleep(100);
                    timeElapsedMillis += 100;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
            }
            totalSecondsToWait -= segmentSeconds;
        }
        System.out.println("\nTimer has ended");
    }

    public static void loadSettings() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            File configFile = new File(System.getProperty("user.dir"), "config.yaml");
            settings = mapper.readValue(configFile, Settings.class);
        } catch (IOException e) {
            System.err.println("Could not read the config file! Make sure config.yaml exists.");
            e.printStackTrace();
        }
    }

    public static void listenForStopCommand() {
        Thread stopThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine();
                    if (input.toLowerCase().contains("stop")) {
                        System.out.println("Stopping after current task...");
                        running = false;
                        break;
                    }
                }
            }
        });
        stopThread.setDaemon(true); // Ensures the app can close gracefully
        stopThread.start();
    }

    public static void loadDatabase() {
        // Instantiate the object cleanly with its settings dependency
        db = new DatabaseService(settings);
        db.initializeDatabase();

        // Pull initial layout datasets
        profileList = db.loadAllProfiles();
//        List<Profile> jsonProfiles = ConsoleUserInterface.loadProfilesWithPrompt(db, settings);


//        if (!jsonProfiles.isEmpty()) {
//            profileList = jsonProfiles;
//        }
        List<Profile> jsonData = db.loadFromJson(settings.getDatabaseBackupPath());

        // Checks that jsonData is not empty, and that the contents of the lists are NOT identical
        if (!jsonData.isEmpty() && !profileList.equals(jsonData)) {
            // Code runs if the JSON data is different from the Database data
            System.out.println("Database Loaded!");
            System.out.println("The Data From the backup JSON file has been changed/updated");
            System.out.println("If you want to load the JSON database, please type 'yes' or 'y' and press Enter. Otherwise, the existing database will be used.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            ExecutorService executor = Executors.newSingleThreadExecutor();

            Future<String> inputFuture = executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    if (reader.ready()) {
                        return reader.readLine();
                    }
                    Thread.sleep(100);
                }
                return null;
            });

            String input = null;
            try {
                for (int i = 10; i > 0; i--) {
                    System.out.print("\rProgram will start in " + i + " seconds... ");
                    System.out.flush();

                    if (inputFuture.isDone()) {
                        input = inputFuture.get();
                        break;
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor.shutdownNow();
            }

            System.out.println("\n--- Starting Program ---");

            if (input != null && (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y"))) {
                profileList = jsonData;
                db.saveAllProfiles(profileList);
                System.out.println("JSON database loaded into the program and saved to the main database!");
            } else {
                System.out.println("Continuing with existing database. JSON data was not loaded.");
            }
        }





        try {
            org.h2.tools.Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
            System.out.println("✅ H2 Console started! Open browser → http://localhost:8082 For debug");
        } catch (SQLException e) {
            System.err.println("Could not bind background H2 Web Debug UI instance.");
        }
    }

    public static void waitForInternet() {
        int retryDelaySeconds = 5;
        System.out.println("Checking internet connection...");

        while (running) {
            if (isInternetAvailable()) {
                System.out.println("\nInternet connection detected!");
                break;
            }

            for (int i = retryDelaySeconds; i > 0; i--) {
                if (!running) break;
                System.out.print("\rNo internet. Retrying in " + i + " seconds...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.err.println("\nThe retry wait was interrupted.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            System.out.print("\rNo internet. Retrying...          ");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static boolean isInternetAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}