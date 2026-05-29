package org.bot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class ProfileProcessor {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final int MAX_SEARCH_PER_DAY = 5;
    private static final int ACTIVITY_THRESHOLD_MINS = 180;

    private final DatabaseService db;
    private final SearchBotService searchBotService;
    private final WebScraperService webScraperService;

    public ProfileProcessor(DatabaseService db, Settings settings) {
        this.db = db;
        // Inject dependencies once on startup!
        this.searchBotService = new SearchBotService(settings);
        this.webScraperService = new WebScraperService(settings);
    }

    public void processAll(List<Profile> profileList) {
        String formattedNow = LocalDateTime.now().format(TIME_FORMATTER);

        for (Profile profile : profileList) {
            Main.waitForInternet();

            if (!Main.running) break;

            processSingleProfile(profile, formattedNow);

            db.saveAllProfiles(profileList);

            long remainingRuns = Main.numberOfRuns;
            while ((remainingRuns - 500) > 0) {
                remainingRuns -= 500;
            }
            Main.waitFor(remainingRuns * 30);
        }
    }

    private void processSingleProfile(Profile profile, String formattedNow) {
        resetSearchCountIfNewDay(profile, formattedNow);

        // 1. Clean Object-Oriented call to Search Service
        if (profile.getTimesSearched() < MAX_SEARCH_PER_DAY) {
            searchBotService.executeSearch(profile.getProfileDir());

            profile.setTimesSearched(profile.getTimesSearched() + 1);
            profile.setLastSearchTime(formattedNow);
        }

        // 2. Clean Object-Oriented call to Scraper Service
        if (Main.running && isActivityCheckNeeded(profile.getLastActivityCheck(), formattedNow)) {
            webScraperService.scrapeDailyActivities(profile.getProfileDir());
            profile.setLastActivityCheck(formattedNow);
        } else {
            System.out.println("Skipping activity check for " + profile.getName() + " as it was recently checked.");
        }
    }

    private void resetSearchCountIfNewDay(Profile profile, String formattedNow) {
        String lastSearchDate = profile.getLastSearchTime().split(" ")[0];
        String currentDate = formattedNow.split(" ")[0];

        if (!Objects.equals(lastSearchDate, currentDate)) {
            profile.setTimesSearched(0);
        }
    }

    private boolean isActivityCheckNeeded(String lastActivityCheck, String formattedNow) {
        try {
            LocalTime currentLocalTime = LocalTime.parse(formattedNow.split(" ")[1]);
            LocalTime lastActivityTime = LocalTime.parse(lastActivityCheck.split(" ")[1]);

            long minutesPassed = Math.abs(Duration.between(currentLocalTime, lastActivityTime).toMinutes());
            return minutesPassed > ACTIVITY_THRESHOLD_MINS;
        } catch (Exception e) {
            return true;
        }
    }
}