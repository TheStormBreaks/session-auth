package com.example.sessionauth.model;

import java.util.List;
import java.util.Map;

public record SpnRecordBatchRequest(
        String categoryKey,
        int startRow,
        List<Map<String, Object>> records) {
}