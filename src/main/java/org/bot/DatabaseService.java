package org.bot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatabaseService {

    private final Settings settings;
    private final String dbUrl;
    private final ObjectMapper objectMapper;

    public DatabaseService(Settings settings) {
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
     * Connects to the database and initializes tables if they do not exist.
     */
    public void initializeDatabase() {
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

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Profiles Database Table Ready!");
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads profiles from a JSON file path.
     */
    public List<Profile> loadFromJson(String filePath) {
        List<Profile> profiles = new ArrayList<>();
        File file = new File(filePath);

        // If the file doesn't exist yet, return an empty list safely
        if (!file.exists()) {
            return profiles;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            // Read the JSON as a Map matching the layout structure {"profiles": [...]}
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
        String insertSql = "INSERT INTO profiles (profileDir, name, lastSearchTime, lastActivityCheck, timesSearched) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Enable atomic execution sequence

            try (Statement deleteStmt = conn.createStatement();
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                deleteStmt.execute(deleteSql);

                for (Profile p : profiles) {
                    insertStmt.setString(1, p.getProfileDir());
                    insertStmt.setString(2, p.getName());
                    insertStmt.setString(3, p.getLastSearchTime());
                    insertStmt.setString(4, p.getLastActivityCheck());
                    insertStmt.setInt(5, p.getTimesSearched());
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
        String sql = "SELECT profileDir, name, lastSearchTime, lastActivityCheck, timesSearched FROM profiles";

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

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, "sa", "");
    }
}