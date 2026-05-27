package org.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProfileDatabase {

    private static final String DB_URL = "jdbc:h2:./assets/profiles;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    // Create table (run once)
    public void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS profiles (
                id INT AUTO_INCREMENT PRIMARY KEY,
                profileDir VARCHAR(100),
                name VARCHAR(150),
                lastSearchTime VARCHAR(50),
                lastActivityCheck VARCHAR(50),
                timesSearched INT
            )
            """;

        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("Profiles Database Table Ready!");

        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    // Load profiles from JSON file into List<Profile>
    public List<Profile> loadFromJson(String jsonFilePath) {
        List<Profile> profiles = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();

            // Read the JSON file
            JsonNode root = mapper.readTree(new File(jsonFilePath));
            JsonNode profilesArray = root.get("profiles");

            if (profilesArray != null && profilesArray.isArray()) {
                for (JsonNode node : profilesArray) {
                    Profile p = new Profile();
                    p.setProfileDir(node.get("profileDir").asText());
                    p.setName(node.get("name").asText());
                    p.setLastSearchTime(node.get("lastSearchTime").asText());
                    p.setLastActivityCheck(node.get("lastActivityCheck").asText());
                    p.setTimesSearched(node.get("timesSearched").asInt());

                    profiles.add(p);
                }
            }

            System.out.println("✅ Loaded " + profiles.size() + " profiles from JSON");

        } catch (Exception e) {
            System.out.println("❌ Error loading JSON: " + e.getMessage());
            e.printStackTrace();
        }

        return profiles;
    }

    // Save all profiles (Replace old data with new list)
    public void saveAllProfiles(List<Profile> profiles) {
        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
            conn.setAutoCommit(false);   // Important for safety

            // Clear old data first
            conn.createStatement().execute("DELETE FROM profiles");

            // Insert new data
            String sql = "INSERT INTO profiles (profileDir, name, lastSearchTime, lastActivityCheck, timesSearched) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Profile p : profiles) {
                    pstmt.setString(1, p.getProfileDir());
                    pstmt.setString(2, p.getName());
                    pstmt.setString(3, p.getLastSearchTime());
                    pstmt.setString(4, p.getLastActivityCheck());
                    pstmt.setInt(5, p.getTimesSearched());
                    pstmt.executeUpdate();
                }
            }

            conn.commit();   // Save everything at once safely
            System.out.println("All profiles saved successfully!");

            System.out.println("Exporting to a json file...");
            exportToJson("assets/Profiles.json");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Export database to readable JSON file
    public void exportToJson(String filePath) {
        List<Profile> profiles = loadAllProfiles();

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);  // Makes it pretty

            mapper.writeValue(new File(filePath), Map.of("profiles", profiles));

            System.out.println("✅ Exported to: " + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load all profiles
    public List<Profile> loadAllProfiles() {
        List<Profile> profiles = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM profiles")) {

            while (rs.next()) {
                Profile p = new Profile();
                p.setProfileDir(rs.getString("profileDir"));
                p.setName(rs.getString("name"));
                p.setLastSearchTime(rs.getString("lastSearchTime"));
                p.setLastActivityCheck(rs.getString("lastActivityCheck"));
                p.setTimesSearched(rs.getInt("timesSearched"));
                profiles.add(p);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }



        return profiles;
    }
}