package org.bot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private final Settings settings;
    private final String dbUrl;
    private final ObjectMapper objectMapper;

    public DatabaseManager(Settings settings) {
        this.settings = settings;
        this.objectMapper = new ObjectMapper();

        // Clean relative path syntax correction safely handling prefixes
        String path = settings.getDatabasePath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        this.dbUrl = "jdbc:h2:./" + path + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    }

    /**
     * Connects to the database, initializes tables if they do not exist,
     * and handles initial seed populating if no data exists.
     */
    public void initializeDatabase() {
        // Change the SQL string inside initializeDatabase() to:
        String sql = """
    CREATE TABLE IF NOT EXISTS profiles (
        id INT AUTO_INCREMENT PRIMARY KEY,
        profileDir VARCHAR(100),
        name VARCHAR(150),
        lastSearchTime VARCHAR(50),
        lastActivityCheck VARCHAR(50),
        timesSearched INT,
        lastUsedProxy VARCHAR(150),
        proxyCountry VARCHAR(2)
    )
    """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Profiles Database Table Ready!");

            // Run conditional checks and data generation rules right after creation
            checkAndPopulateDatabase(conn);

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Syncs the database table with the current settings profile list.
     * Automatically adds missing profiles and removes deleted ones without losing existing data.
     */
    private void checkAndPopulateDatabase(Connection conn) throws SQLException {
        // 1. Safety check for properties structures
        if (settings.getProfiles() == null || settings.getProfiles().getLocalFolderList() == null) {
            System.err.println("⚠️ Warning: Could not sync database because settings profiles configuration is null.");
            return;
        }

        List<String> expectedFolders = settings.getProfiles().getLocalFolderList();

        // 2. Fetch all currently existing profile directories from the database
        List<String> existingFoldersInDb = new ArrayList<>();
        String selectSql = "SELECT profileDir FROM profiles";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {
            while (rs.next()) {
                existingFoldersInDb.add(rs.getString("profileDir"));
            }
        }

        // 3. Find and INSERT missing profiles (in settings list but NOT in DB)
        List<String> foldersToAdd = new ArrayList<>();
        for (String folder : expectedFolders) {
            if (!existingFoldersInDb.contains(folder)) {
                foldersToAdd.add(folder);
            }
        }

        if (!foldersToAdd.isEmpty()) {
            System.out.println("Found " + foldersToAdd.size() + " new profile(s) in settings. Adding to database...");
            String insertSql = """
INSERT INTO profiles (profileDir, name, lastSearchTime, lastActivityCheck, timesSearched, lastUsedProxy, proxyCountry) 
VALUES (?, ?, ?, ?, ?, ?, ?)
""";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (String folder : foldersToAdd) {
                    ps.setString(1, folder);                        // profileDir assigned as folder string element
                    ps.setString(2, "");                            // name left empty
                    ps.setString(3, "00-00-0000 00:00:00");         // Default time format
                    ps.setString(4, "00-00-0000 00:00:00");         // Default time format
                    ps.setInt(5, 0);                                // Integer as 0
                    ps.setString(6, "0.0.0.0");
                    ps.setString(7, "");
                    ps.addBatch();
                }
                ps.executeBatch();
                System.out.println("✅ Successfully added new profiles.");
            }
        }

        // 4. Find and DELETE removed profiles (in DB but NOT in settings list)
        List<String> foldersToRemove = new ArrayList<>();
        for (String dbFolder : existingFoldersInDb) {
            if (!expectedFolders.contains(dbFolder)) {
                foldersToRemove.add(dbFolder);
            }
        }

        if (!foldersToRemove.isEmpty()) {
            System.out.println("Found " + foldersToRemove.size() + " obsolete profile(s) removed from settings. Cleaning up database...");
            String deleteSql = "DELETE FROM profiles WHERE profileDir = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                for (String folder : foldersToRemove) {
                    ps.setString(1, folder);
                    ps.addBatch();
                }
                ps.executeBatch();
                System.out.println("✅ Successfully cleaned up old profiles.");
            }
        }

        if (foldersToAdd.isEmpty() && foldersToRemove.isEmpty()) {
            System.out.println("Database is already perfectly in sync with your settings profile list!");
        }
    }

    /**
     * Loads profiles from a JSON file path.
     */
    public List<Profile> loadFromJson(String filePath) {
        List<Profile> profiles = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            return profiles;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<Profile>> data = mapper.readValue(
                    file,
                    new TypeReference<Map<String, List<Profile>>>() {}
            );

            if (data != null && data.containsKey("profiles")) {
                profiles = data.get("profiles");
            }
        } catch (IOException e) {
            System.err.println("Failed to parse JSON backup file: " + e.getMessage());
        }
        return profiles;
    }

    /**
     * Atomically saves all profiles using transaction controls and exports a JSON backup.
     */
    public void saveAllProfiles(List<Profile> profiles) {
        String deleteSql = "DELETE FROM profiles";
        String insertSql = "INSERT INTO profiles (profileDir, name, lastSearchTime, lastActivityCheck, timesSearched, lastUsedProxy, proxyCountry) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (Statement deleteStmt = conn.createStatement();
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                deleteStmt.execute(deleteSql);

                for (Profile p : profiles) {
                    insertStmt.setString(1, p.getProfileDir());
                    insertStmt.setString(2, p.getName());
                    insertStmt.setString(3, p.getLastSearchTime());
                    insertStmt.setString(4, p.getLastActivityCheck());
                    insertStmt.setInt(5, p.getTimesSearched());
                    insertStmt.setString(6, p.getLastUsedProxy());
                    insertStmt.setString(7, p.getProxyCountry());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
                System.out.println("All profiles saved successfully!");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

            System.out.println("Exporting to a JSON file...");
            exportToJson(settings.getDatabaseBackupPath(), profiles);

        } catch (SQLException e) {
            System.err.println("Failed to write transaction records to profiles table: " + e.getMessage());
        }
    }

    /**
     * Query profiles cleanly from SQL table.
     */
    public List<Profile> loadAllProfiles() {
        List<Profile> profiles = new ArrayList<>();
        String sql = "SELECT profileDir, name, lastSearchTime, lastActivityCheck, timesSearched, lastUsedProxy, proxyCountry FROM profiles";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Profile p = new Profile();
                p.setProfileDir(rs.getString("profileDir"));
                p.setName(rs.getString("name"));
                p.setLastSearchTime(rs.getString("lastSearchTime"));
                p.setLastActivityCheck(rs.getString("lastActivityCheck"));
                p.setTimesSearched(rs.getInt("timesSearched"));
                p.setLastUsedProxy(rs.getString("lastUsedProxy"));
                p.setProxyCountry(rs.getString("proxyCountry"));
                profiles.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Failed to read context profiles: " + e.getMessage());
        }
        return profiles;
    }

    private void exportToJson(String filePath, List<Profile> profiles) {
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(filePath), Map.of("profiles", profiles));
            System.out.println("✅ Exported to: " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to export database schema payload to JSON: " + e.getMessage());
        }
    }

    /**
     * Updates only the proxy details for a specific profile directory.
     */
    public void updateProfileProxy(String profileDir, String proxyAddress, String proxyCountry) {
        String sql = "UPDATE profiles SET lastUsedProxy = ?, proxyCountry = ? WHERE profileDir = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, proxyAddress);
            ps.setString(2, proxyCountry);
            ps.setString(3, profileDir);

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("✅ Database updated with proxy details for profile: " + profileDir);
            }
        } catch (SQLException e) {
            System.err.println("Failed to update profile proxy in database: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, "sa", "");
    }
}