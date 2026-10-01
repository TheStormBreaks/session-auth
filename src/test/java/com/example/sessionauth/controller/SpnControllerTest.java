package com.example.sessionauth.controller;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.sessionauth.model.SpnRecordBatchRequest;
import com.example.sessionauth.model.SpnUploadRequest;
import com.example.sessionauth.service.SpnDataService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class SpnControllerTest {

    private static final String OWNER = "spn-controller-test";

    @Autowired
    private SpnDataService spnDataService;

    @Autowired
    private ObjectMapper objectMapper;

    private String uploadId;

    @AfterEach
    @SuppressWarnings("unused")
    void cleanUp() {
        if (uploadId != null) spnDataService.deleteUpload(OWNER, uploadId);
    }

    @Test
    void excelEndpointReturnsDownloadableWorkbook() throws Exception {
        List<SpnUploadRequest.Category> categories = List.of(
                new SpnUploadRequest.Category("sets/contractDef", "sets / contractDef", 1, List.of("code")));
        SpnUploadRequest upload = new SpnUploadRequest(
                "sample.spn", 128, "4.00", "202609302000", "20260930", "Test Clearing", 1, categories);
        uploadId = spnDataService.createUpload(OWNER, upload);
        String longValue = "CL".repeat(20000);
        spnDataService.saveRecordBatch(OWNER, uploadId,
            new SpnRecordBatchRequest("sets/contractDef", 0, List.of(Map.of("code", longValue))));
        spnDataService.completeUpload(OWNER, uploadId);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", OWNER);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SpnController(spnDataService)).build();

        MvcResult createdResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/spn/uploads/{uploadId}/records/new", uploadId)
                .param("categoryKey", "sets/contractDef")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NEW\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode createdRecord = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        long createdRecordId = createdRecord.get("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/spn/uploads/{uploadId}/records/{recordId}", uploadId, createdRecordId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"UPDATED\"}"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.values.code").value("UPDATED"));

        MvcResult result = mockMvc.perform(get("/api/spn/uploads/{uploadId}/excel", uploadId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parsed-spn-data.xlsx\""))
                .andReturn();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("code (continued 2)");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()
                    + workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo(longValue);
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("UPDATED");
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/spn/uploads/{uploadId}/records/{recordId}", uploadId, createdRecordId).session(session))
            .andExpect(status().isNoContent());
    }
}