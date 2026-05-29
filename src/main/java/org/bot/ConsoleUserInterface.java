package org.bot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConsoleUserInterface {

    public static List<Profile> loadProfilesWithPrompt(DatabaseService db, Settings settings) {
        List<Profile> profiles = new ArrayList<>();
        System.out.println("Database Loaded!");
        System.out.println("If you want to load another Data from database_backup.json Enter [yes/y] before the program starts.");

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

        if (input != null) {
            String cleanInput = input.trim().toLowerCase();
            if (cleanInput.equals("yes") || cleanInput.equals("y")) {
                System.out.println("Reloading profiles...");
                return db.loadFromJson(settings.getDatabaseBackupPath());
            }
        }
        return profiles;
    }
}