package com.example.sessionauth.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.sessionauth.model.SpnRecordBatchRequest;
import com.example.sessionauth.model.SpnUploadRequest;

@SpringBootTest
class SpnDataServiceTest {

    @Autowired
    private SpnDataService spnDataService;

    private String uploadId;
    private String owner;

    @AfterEach
        @SuppressWarnings("unused")
    void cleanUp() {
        if (uploadId != null) spnDataService.deleteUpload(owner, uploadId);
    }

    @Test
    void storesRecordsAndReturnsTenRowsPerPage() throws Exception {
        owner = "spn-test-user";
        List<SpnUploadRequest.Category> categories = List.of(
                new SpnUploadRequest.Category("definitions/itemDef", "definitions / itemDef", 25, List.of("id", "value")));
        SpnUploadRequest upload = new SpnUploadRequest(
                "sample.spn", 4096, "4.00", "202609302000", "20260930", "Test Clearing", 25, categories);
        uploadId = spnDataService.createUpload(owner, upload);

        List<Map<String, Object>> records = java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> Map.<String, Object>of("id", index, "value", index == 12 ? "MATCH" : "row"))
                .toList();
        // Save fixed row ranges as separate requests, just like the frontend importer.
        spnDataService.saveRecordBatch(owner, uploadId, new SpnRecordBatchRequest("definitions/itemDef", 0, records.subList(0, 10)));
        spnDataService.saveRecordBatch(owner, uploadId, new SpnRecordBatchRequest("definitions/itemDef", 10, records.subList(10, 20)));
        spnDataService.saveRecordBatch(owner, uploadId, new SpnRecordBatchRequest("definitions/itemDef", 20, records.subList(20, 25)));
        spnDataService.completeUpload(owner, uploadId);

        Map<String, Object> firstPage = spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 0, null);
        Map<String, Object> secondPage = spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 1, null);
        Map<String, Object> searchResults = spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 0, "match");
        Map<String, Object> latestUpload = spnDataService.latestUpload(owner);

        assertThat((List<?>) firstPage.get("items")).hasSize(10);
        assertThat((List<?>) secondPage.get("items")).hasSize(10);
        assertThat(firstPage).containsEntry("totalRecords", 25).containsEntry("pageSize", 10);
        assertThat(searchResults).containsEntry("totalRecords", 1);
        assertThat(latestUpload).containsEntry("fileName", "sample.spn");
        assertThatThrownBy(() -> spnDataService.pageRecords("another-user", uploadId, "definitions/itemDef", 0, null))
                .isInstanceOf(IllegalArgumentException.class);

        Map<?, ?> firstRecord = (Map<?, ?>) ((List<?>) firstPage.get("items")).get(0);
        long firstRecordId = ((Number) firstRecord.get("id")).longValue();
        Map<String, Object> updated = spnDataService.updateRecord(owner, uploadId, firstRecordId,
            Map.of("id", 0, "value", "EDITED"));
        assertThat(((Map<?, ?>) updated.get("values")).get("value")).isEqualTo("EDITED");
        assertThat(spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 0, "edited"))
            .containsEntry("totalRecords", 1);

        Map<String, Object> created = spnDataService.createRecord(owner, uploadId, "definitions/itemDef",
            Map.of("id", 25, "value", "CREATED"));
        long createdId = ((Number) created.get("id")).longValue();
        assertThat(spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 0, null))
            .containsEntry("totalRecords", 26);
        spnDataService.deleteRecord(owner, uploadId, createdId);
        assertThat(spnDataService.pageRecords(owner, uploadId, "definitions/itemDef", 0, null))
            .containsEntry("totalRecords", 25);

        ByteArrayOutputStream export = new ByteArrayOutputStream();
        spnDataService.writeExcelExport(owner, uploadId, export);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("definitions itemDef");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("EDITED");
            assertThat(workbook.getSheetAt(0).getRow(13).getCell(1).getStringCellValue()).isEqualTo("MATCH");
        }
    }
}