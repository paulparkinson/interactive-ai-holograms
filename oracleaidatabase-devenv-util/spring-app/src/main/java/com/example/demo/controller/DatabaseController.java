package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api/databases")
public class DatabaseController {

    @Value("${DB_PROD_WALLET_DIR:/wallet}")
    private String walletDir;

    @Value("${DB_PROD_WALLET_SERVICE:}")
    private String walletService;

    @Value("${DB_PROD_USER:}")
    private String prodUser;

    @Value("${DB_PROD_PWD:}")
    private String prodPassword;

    private Connection activeConnection;
    private String activeDatabase = "default";

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDatabases() {
        List<Map<String, Object>> databases = new ArrayList<>();

        // Default database (local Oracle)
        Map<String, Object> defaultDb = new HashMap<>();
        defaultDb.put("name", "default");
        defaultDb.put("wallet", false);
        defaultDb.put("is_active", "default".equals(activeDatabase));
        databases.add(defaultDb);

        // Prod database (Autonomous DB with wallet)
        if (walletService != null && !walletService.isEmpty()) {
            Map<String, Object> prodDb = new HashMap<>();
            prodDb.put("name", "prod");
            prodDb.put("wallet", true);
            prodDb.put("service", walletService);
            prodDb.put("is_active", "prod".equals(activeDatabase));
            databases.add(prodDb);
        }

        // Wrap in "databases" key for HTML compatibility
        Map<String, Object> response = new HashMap<>();
        response.put("databases", databases);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchDatabase(@RequestBody Map<String, String> request) {
        String databaseName = request.get("database_name");
        Map<String, Object> response = new HashMap<>();

        try {
            // Close existing connection
            if (activeConnection != null && !activeConnection.isClosed()) {
                activeConnection.close();
            }

            if ("prod".equals(databaseName)) {
                // Connect to Autonomous Database using wallet
                activeConnection = connectToAutonomousDatabase();
                activeDatabase = "prod";
                response.put("success", true);
                response.put("message", "Switched to prod database");
            } else {
                // For now, just mark as switched - actual connection would be to local Oracle
                activeDatabase = "default";
                response.put("success", true);
                response.put("message", "Switched to default database");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Connection failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (activeConnection == null || activeConnection.isClosed()) {
                response.put("success", false);
                response.put("error", "No active connection");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Test the connection with a simple query
            var stmt = activeConnection.createStatement();
            var rs = stmt.executeQuery("SELECT 'Connection OK' as status FROM DUAL");
            if (rs.next()) {
                response.put("success", true);
                response.put("status", rs.getString("status"));
                response.put("database", activeDatabase);
            }
            rs.close();
            stmt.close();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (activeConnection == null || activeConnection.isClosed()) {
                response.put("success", false);
                response.put("error", "No active connection");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Query documents table
            var stmt = activeConnection.createStatement();
            var rs = stmt.executeQuery(
                "SELECT id, name, chunk_index, DBMS_LOB.SUBSTR(text, 200, 1) as text_preview, " +
                "DBMS_LOB.GETLENGTH(text) as text_length " +
                "FROM documents ORDER BY name, chunk_index"
            );

            List<Map<String, Object>> documents = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", rs.getString("id"));
                doc.put("name", rs.getString("name"));
                doc.put("chunk_index", rs.getInt("chunk_index"));
                doc.put("text_preview", rs.getString("text_preview"));
                doc.put("text_length", rs.getInt("text_length"));
                documents.add(doc);
            }
            rs.close();
            stmt.close();

            response.put("success", true);
            response.put("documents", documents);
            response.put("count", documents.size());
            response.put("database", activeDatabase);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private Connection connectToAutonomousDatabase() throws SQLException {
        try {
            // Set wallet location system properties
            System.setProperty("oracle.net.tns_admin", walletDir);
            System.setProperty("oracle.net.wallet_location", walletDir);

            // Build connection URL for wallet-based connection
            String url = "jdbc:oracle:thin:@" + walletService;

            Properties props = new Properties();
            props.setProperty("user", prodUser);
            props.setProperty("password", prodPassword);

            System.out.println("Attempting connection to: " + url);
            System.out.println("Wallet dir: " + walletDir);
            System.out.println("Service: " + walletService);

            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            System.err.println("Failed to connect to Autonomous Database: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
