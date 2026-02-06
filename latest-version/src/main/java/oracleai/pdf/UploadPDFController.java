package oracleai.pdf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(originPatterns = "*")
public class UploadPDFController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Upload a PDF file to the MY_BOOKS table
     * 
     * @param file The uploaded file
     * @return ResponseEntity with upload status and file ID
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPDF(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String fileName = file.getOriginalFilename();
            String fileType = file.getContentType();
            long fileSize = file.getSize();
            byte[] fileContent = file.getBytes();

            // Get the next sequence value first
            Integer id = jdbcTemplate.queryForObject("SELECT my_books_seq.NEXTVAL FROM dual", Integer.class);

            // Insert into MY_BOOKS table with the generated ID
            String sql = "INSERT INTO my_books (id, file_name, file_size, file_type, file_content, created_on) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
            
            jdbcTemplate.update(sql, 
                id,
                fileName, 
                fileSize, 
                fileType, 
                fileContent,
                new Timestamp(System.currentTimeMillis())
            );

            response.put("success", true);
            response.put("id", id);
            response.put("fileName", fileName);
            response.put("fileSize", fileSize);
            response.put("fileType", fileType);
            response.put("message", "File uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (DuplicateKeyException e) {
            response.put("success", false);
            response.put("error", "This file already exists in the database (same name and size). Please delete the existing file first or rename this file.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * List all uploaded files
     * 
     * @return List of all files in MY_BOOKS table
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listFiles() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "SELECT id, file_name, file_size, file_type, created_on FROM my_books ORDER BY created_on DESC";
            
            List<Map<String, Object>> files = jdbcTemplate.queryForList(sql);
            
            response.put("success", true);
            response.put("files", files);
            response.put("count", files.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download a file by ID
     * 
     * @param id The file ID
     * @return The file content
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Integer id) {
        try {
            String sql = "SELECT file_name, file_type, file_content FROM my_books WHERE id = ?";
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, id);
            
            String fileName = (String) result.get("FILE_NAME");
            String fileType = (String) result.get("FILE_TYPE");
            byte[] fileContent = (byte[]) result.get("FILE_CONTENT");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileType))
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .body(fileContent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    /**
     * Delete a file by ID
     * 
     * @param id The file ID
     * @return ResponseEntity with deletion status
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "DELETE FROM my_books WHERE id = ?";
            int rowsAffected = jdbcTemplate.update(sql, id);
            
            if (rowsAffected > 0) {
                response.put("success", true);
                response.put("message", "File deleted successfully");
                response.put("id", id);
            } else {
                response.put("success", false);
                response.put("error", "File not found");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Query Oracle AI function generate_text_response2
     * 
     * @param question The text question
     * @param param1 First numeric parameter
     * @param param2 Second numeric parameter
     * @return ResponseEntity with the AI response
     */
    @PostMapping("/query-ai")
    public ResponseEntity<Map<String, Object>> queryAI(
            @RequestParam("question") String question,
            @RequestParam("param1") Integer param1,
            @RequestParam("param2") Integer param2) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "SELECT generate_text_response2(?, ?, ?) AS response FROM dual";
            
            String aiResponse = jdbcTemplate.queryForObject(sql, String.class, question, param1, param2);
            
            // Also get the snippets and file names from my_books table based on vector similarity
            String snippetSql = "SELECT id, file_name, " +
                               "DBMS_LOB.SUBSTR(file_content, 500, 1) AS snippet, " +
                               "file_size, created_on " +
                               "FROM my_books " +
                               "ORDER BY created_on DESC " +
                               "FETCH FIRST ? ROWS ONLY";
            
            List<Map<String, Object>> snippets = jdbcTemplate.queryForList(snippetSql, param2);
            
            response.put("success", true);
            response.put("question", question);
            response.put("param1", param1);
            response.put("param2", param2);
            response.put("response", aiResponse);
            response.put("snippets", snippets);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Query Oracle AI function generate_text_response_all_docs with vector search
     * 
     * @param question The text question
     * @param topn Number of top results to fetch
     * @return ResponseEntity with the AI response and doc IDs
     */
    @PostMapping("/query-vector-ai")
    public ResponseEntity<Map<String, Object>> queryVectorAI(
            @RequestParam("question") String question,
            @RequestParam("topn") Integer topn) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Try the Ollama-based function first
            String sql = "SELECT generate_text_response_all_docs(?, ?) AS response FROM dual";
            
            String jsonResponse = jdbcTemplate.queryForObject(sql, String.class, question, topn);
            
            response.put("success", true);
            response.put("question", question);
            response.put("topn", topn);
            response.put("result", jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Query vector search only (without Ollama) - returns matching chunks
     * 
     * @param question The text question
     * @param topn Number of top results to fetch
     * @return ResponseEntity with matching chunks and doc IDs
     */
    @PostMapping("/query-vector-search")
    public ResponseEntity<Map<String, Object>> queryVectorSearch(
            @RequestParam("question") String question,
            @RequestParam("topn") Integer topn) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "SELECT generate_text_response_vector_only(?, ?) AS response FROM dual";
            
            String jsonResponse = jdbcTemplate.queryForObject(sql, String.class, question, topn);
            
            response.put("success", true);
            response.put("question", question);
            response.put("topn", topn);
            response.put("result", jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
