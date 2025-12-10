package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Java-only database operations.
 * Serves the index_java.html page and provides endpoints that bypass the Python backend.
 */
@Controller
public class JavaOnlyController {

    @Autowired
    private DatabaseController databaseController;

    /**
     * Serve the Java-only interface page
     */
    @GetMapping("/java")
    public String indexJava() {
        return "index_java";
    }

    /**
     * List available databases (Java-only implementation)
     */
    @GetMapping("/api/databases")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listDatabases() {
        return databaseController.getDatabases();
    }

    /**
     * Switch active database connection (Java-only implementation)
     */
    @PostMapping("/api/databases/switch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> switchDatabase(@RequestBody Map<String, String> body) {
        return databaseController.switchDatabase(body);
    }

    /**
     * Test the active database connection (Java-only implementation)
     */
    @GetMapping("/api/databases/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection() {
        return databaseController.testConnection();
    }

    /**
     * List documents from the active database (Java-only implementation)
     */
    @GetMapping("/api/databases/documents")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listDocuments() {
        return databaseController.listDocuments();
    }
}
