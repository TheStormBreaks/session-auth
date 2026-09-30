package com.example.sessionauth.model;

import java.util.List;

public record SpnUploadRequest(
        String fileName,
        long fileSize,
        String format,
        String created,
        String dataDate,
        String organization,
        int recordCount,
        List<Category> categories) {

    public record Category(String key, String label, int recordCount, List<String> columns) {
    }
}