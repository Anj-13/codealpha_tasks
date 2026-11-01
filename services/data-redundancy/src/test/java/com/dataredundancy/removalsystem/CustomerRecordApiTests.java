package com.dataredundancy.removalsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.dataredundancy.removalsystem.repository.CustomerRecordRepository;

@SpringBootTest(properties = "app.admin-key=test-admin-key")
@AutoConfigureMockMvc
class CustomerRecordApiTests {

    private static final String AVA = """
        {"fullName":"Ava Thompson","email":"ava@example.com","phone":"07123 456 789","address":"21 King Street"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CustomerRecordRepository repository;

    @BeforeEach
    void clearRecords() {
        repository.deleteAll();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void storesANewRecord() throws Exception {
        postJson("/api/records", AVA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("UNIQUE"))
            .andExpect(jsonPath("$.stored").value(true))
            .andExpect(jsonPath("$.phone").value("07123456789"));
    }

    @Test
    void rejectsAnExactDuplicate() throws Exception {
        postJson("/api/records", AVA);

        postJson("/api/records", AVA)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value("REDUNDANT"))
            .andExpect(jsonPath("$.stored").value(false));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void formattingDoesNotHideADuplicate() throws Exception {
        postJson("/api/records", AVA);

        postJson("/api/records", """
            {"fullName":"  ava   THOMPSON ","email":"AVA@example.com","phone":"07123456789","address":"21 king street"}""")
            .andExpect(status().isConflict());
    }

    @Test
    void sameEmailAndPhoneUnderAnotherNameIsRedundant() throws Exception {
        postJson("/api/records", AVA);

        postJson("/api/records", """
            {"fullName":"Someone Else","email":"ava@example.com","phone":"07123456789"}""")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.validationReason").value("Duplicate detected from matching email and phone"));
    }

    @Test
    void partialMatchIsHeldForReview() throws Exception {
        postJson("/api/records", AVA);

        postJson("/api/records", """
            {"fullName":"Ava Thompson","email":"ava@example.com","phone":"07000 000 000"}""")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void validateClassifiesWithoutStoring() throws Exception {
        postJson("/api/records/validate", AVA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNIQUE"))
            .andExpect(jsonPath("$.stored").value(false));
        assertThat(repository.count()).isZero();

        postJson("/api/records", AVA);
        postJson("/api/records/validate", AVA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REDUNDANT"));
    }

    @Test
    void reportsInvalidFieldsUnderTheirApiNames() throws Exception {
        postJson("/api/records", """
            {"fullName":"","email":"not-an-email","phone":"letters-only"}""")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required"))
            .andExpect(jsonPath("$.fieldErrors.email").value("Invalid email format"))
            .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    @Test
    void malformedJsonUsesTheSharedErrorShape() throws Exception {
        postJson("/api/records", "{bad")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingRecordUsesTheSharedErrorShape() throws Exception {
        mvc.perform(get("/api/records/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Record not found with id: 999999"));
    }

    @Test
    void publicReadsAreMasked() throws Exception {
        postJson("/api/records", AVA);

        mvc.perform(get("/api/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].masked").value(true))
            .andExpect(jsonPath("$[0].fullName").value("A*** T***"))
            .andExpect(jsonPath("$[0].email").value("a***@example.com"))
            .andExpect(jsonPath("$[0].phone").value("********789"))
            .andExpect(jsonPath("$[0].address").value(nullValue()))
            .andExpect(content().string(not(containsString("King Street"))));
    }

    @Test
    void adminKeyGivesFullRecords() throws Exception {
        postJson("/api/records", AVA);

        mvc.perform(get("/api/records").header("X-Admin-Key", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].masked").value(false))
            .andExpect(jsonPath("$[0].email").value("ava@example.com"))
            .andExpect(jsonPath("$[0].address").value("21 King Street"));
    }

    @Test
    void wrongAdminKeyIsRefusedRatherThanMasked() throws Exception {
        mvc.perform(get("/api/records").header("X-Admin-Key", "wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void summaryCountsStoredRecords() throws Exception {
        mvc.perform(get("/api/records/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRecords").value(0))
            .andExpect(jsonPath("$.lastStoredAt").value(nullValue()));

        postJson("/api/records", AVA);

        mvc.perform(get("/api/records/summary"))
            .andExpect(jsonPath("$.totalRecords").value(1))
            .andExpect(jsonPath("$.lastStoredAt").exists());
    }
}
