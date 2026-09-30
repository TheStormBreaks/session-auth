package com.example.sessionauth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sessionauth.model.SpnRecordBatchRequest;
import com.example.sessionauth.model.SpnUploadRequest;
import com.example.sessionauth.service.SpnDataService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/spn/uploads")
public class SpnController {

    private static final int MAX_BATCH_SIZE = 1000;

    private final SpnDataService spnDataService;

    public SpnController(SpnDataService spnDataService) {
        this.spnDataService = spnDataService;
    }

    @PostMapping
    public ResponseEntity<?> createUpload(HttpSession session, @RequestBody SpnUploadRequest request) {
        String owner = currentUser(session);
        if (owner == null) return unauthorized();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", spnDataService.createUpload(owner, request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latestUpload(HttpSession session) {
        String owner = currentUser(session);
        if (owner == null) return unauthorized();
        Map<String, Object> upload = spnDataService.latestUpload(owner);
        return upload == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No saved SPN file."))
                : ResponseEntity.ok(upload);
    }

    @PostMapping("/{uploadId}/records")
    public ResponseEntity<?> saveRecordBatch(HttpSession session, @PathVariable String uploadId,
                                              @RequestBody SpnRecordBatchRequest batch) {
        String owner = currentUser(session);
        if (owner == null) return unauthorized();
        if (batch.records() == null || batch.records().size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Record batches must contain between 1 and 1000 records."));
        }
        try {
            int saved = spnDataService.saveRecordBatch(owner, uploadId, batch);
            return ResponseEntity.ok(Map.of("saved", saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<?> completeUpload(HttpSession session, @PathVariable String uploadId) {
        String owner = currentUser(session);
        if (owner == null) return unauthorized();
        try {
            spnDataService.completeUpload(owner, uploadId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> deleteUpload(HttpSession session, @PathVariable String uploadId) {
        String owner = currentUser(session);
        if (owner == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        spnDataService.deleteUpload(owner, uploadId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uploadId}/records")
    public ResponseEntity<?> pageRecords(HttpSession session, @PathVariable String uploadId,
                                          @RequestParam String categoryKey,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(required = false) String query) {
        String owner = currentUser(session);
        if (owner == null) return unauthorized();
        try {
            return ResponseEntity.ok(spnDataService.pageRecords(owner, uploadId, categoryKey, page, query));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private String currentUser(HttpSession session) {
        Object user = session.getAttribute("user");
        return user instanceof String username ? username : null;
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Session expired"));
    }
}