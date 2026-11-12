package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.RequestStatusHistory;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.RequestStatusHistoryRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private RequestStatusHistoryRepository statusHistoryRepository;

    @Test
    void employeeCanCreateRequestWithDefaults() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");
        long categoryId = categoryRepository.findAll().getFirst().getId();

        // requesterId is intentionally supplied to prove the server ignores it.
        String body = """
                {"title":"Laptop will not boot",
                 "description":"The laptop shows a black screen after powering on.",
                 "categoryId":%d,
                 "location":"HQ-2-14",
                 "requesterId":999999}
                """.formatted(categoryId);

        String response = mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.referenceNumber")
                        .value(matchesPattern("SR-\\d{4}-\\d{4,}")))
                .andExpect(jsonPath("$.title").value("Laptop will not boot"))
                .andExpect(jsonPath("$.category.id").value(categoryId))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.location").value("HQ-2-14"))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.requester.id").value(4))
                .andExpect(jsonPath("$.department.id").value(1))
                .andExpect(jsonPath("$.assignedTo").value((Object) null))
                .andExpect(jsonPath("$.resolvedAt").value((Object) null))
                .andExpect(jsonPath("$.closedAt").value((Object) null))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long requestId = ((Number) JsonPath.read(response, "$.id")).longValue();

        ServiceRequest saved = serviceRequestRepository.findById(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(saved.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(saved.getRequester().getEmail()).isEqualTo("employee@servicehub.local");

        List<RequestStatusHistory> history =
                statusHistoryRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isNull();
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(history.getFirst().getChangedBy().getEmail())
                .isEqualTo("employee@servicehub.local");
        assertThat(history.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void createRequestUsesSuppliedPriorityAndDepartment() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");
        long categoryId = categoryRepository.findAll().getFirst().getId();

        String body = """
                {"title":"Broken air conditioner",
                 "description":"The air conditioner on the second floor is not cooling.",
                 "categoryId":%d,
                 "priority":"HIGH",
                 "departmentId":2,
                 "location":"  Building B, Floor 2  "}
                """.formatted(categoryId);

        mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.department.id").value(2))
                .andExpect(jsonPath("$.location").value("Building B, Floor 2"));
    }

    @Test
    void createRequestWithMissingRequiredFieldsReturnsBadRequest() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
    }

    @Test
    void createRequestWithUnknownCategoryReturnsNotFound() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        String body = """
                {"title":"Unknown category",
                 "description":"Category does not exist.",
                 "categoryId":999999}
                """;

        mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequestWithUnknownDepartmentReturnsNotFound() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");
        long categoryId = categoryRepository.findAll().getFirst().getId();

        String body = """
                {"title":"Unknown department",
                 "description":"Department does not exist.",
                 "categoryId":%d,
                 "departmentId":999999}
                """.formatted(categoryId);

        mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequestWithInvalidPriorityReturnsBadRequest() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");
        long categoryId = categoryRepository.findAll().getFirst().getId();

        String body = """
                {"title":"Invalid priority",
                 "description":"Priority is not a supported value.",
                 "categoryId":%d,
                 "priority":"URGENT"}
                """.formatted(categoryId);

        mockMvc.perform(post("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequestWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }
}
