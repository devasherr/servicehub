package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.ServiceCategory;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceRequestUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Test
    void creatorCanUpdateOwnRequest() throws Exception {
        List<ServiceCategory> categories = categoryRepository.findAll(Sort.by("id"));
        long secondCategoryId = categories.get(1).getId();

        ServiceRequest request = persist("Original title", "employee@servicehub.local", 1L,
                Priority.LOW, RequestStatus.SUBMITTED, null);
        String originalReference = request.getReferenceNumber();
        String token = login("employee@servicehub.local", "Password123!");

        String body = """
                {"title":"  Updated title  ",
                 "description":"  Updated description  ",
                 "categoryId":%d,
                 "priority":"CRITICAL",
                 "location":"  New location  "}
                """.formatted(secondCategoryId);

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.category.id").value(secondCategoryId))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.location").value("New location"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.referenceNumber").value(originalReference))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"));

        ServiceRequest saved = serviceRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Updated title");
        assertThat(saved.getCategory().getId()).isEqualTo(secondCategoryId);
        assertThat(saved.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
    }

    @Test
    void updateIgnoresProtectedFields() throws Exception {
        long categoryId = firstCategoryId();
        ServiceRequest request = persist("Protected fields", "employee@servicehub.local", 1L,
                Priority.LOW, RequestStatus.SUBMITTED, null);
        String originalReference = request.getReferenceNumber();
        String token = login("employee@servicehub.local", "Password123!");

        String body = """
                {"title":"Protected fields ignored",
                 "description":"Updated description",
                 "categoryId":%d,
                 "priority":"HIGH",
                 "location":"Updated location",
                 "status":"CLOSED",
                 "requesterId":1,
                 "assignedToId":3,
                 "departmentId":2,
                 "referenceNumber":"HACKED-0001",
                 "resolvedAt":"2020-01-01T00:00:00Z"}
                """.formatted(categoryId);

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Protected fields ignored"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.referenceNumber").value(originalReference))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.department.id").value(1))
                .andExpect(jsonPath("$.assignedTo").value((Object) null))
                .andExpect(jsonPath("$.resolvedAt").value((Object) null));
    }

    @Test
    void adminCanUpdateAnyRequest() throws Exception {
        ServiceRequest request = persist("Admin update target", "employee@servicehub.local", 2L,
                Priority.LOW, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Admin updated", firstCategoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin updated"))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"));
    }

    @Test
    void managerCanUpdateDepartmentButNotOtherDepartment() throws Exception {
        ServiceRequest departmentRequest = persist("Manager department target",
                "employee@servicehub.local", 1L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest otherDepartmentRequest = persist("Other department target",
                "employee@servicehub.local", 2L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", departmentRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Manager updated", firstCategoryId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/requests/{id}", otherDepartmentRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Manager forbidden", firstCategoryId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCanUpdateAssignedButNotUnassigned() throws Exception {
        ServiceRequest assigned = persist("Assigned target", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.ASSIGNED, "tech@servicehub.local");
        ServiceRequest unassigned = persist("Unassigned target", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Technician updated", firstCategoryId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/requests/{id}", unassigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Technician forbidden", firstCategoryId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotUpdateAnotherEmployeesRequest() throws Exception {
        ServiceRequest request = persist("Technician own target", "tech@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Employee forbidden", firstCategoryId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedRequestCanOnlyBeUpdatedByAdmin() throws Exception {
        ServiceRequest closed = persist("Closed request", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.CLOSED, null);

        String employeeToken = login("employee@servicehub.local", "Password123!");
        mockMvc.perform(put("/api/requests/{id}", closed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Employee closed update", firstCategoryId())))
                .andExpect(status().isConflict());

        String adminToken = login("admin@servicehub.local", "Password123!");
        mockMvc.perform(put("/api/requests/{id}", closed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Admin closed update", firstCategoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin closed update"));
    }

    @Test
    void omittedOptionalFieldsAreKept() throws Exception {
        long categoryId = firstCategoryId();
        ServiceRequest request = persist("Keep optional fields", "employee@servicehub.local", 1L,
                Priority.HIGH, RequestStatus.SUBMITTED, null);
        request.setLocation("Existing location");
        serviceRequestRepository.saveAndFlush(request);

        String token = login("employee@servicehub.local", "Password123!");
        String body = """
                {"title":"Optional fields kept",
                 "description":"Updated description",
                 "categoryId":%d}
                """.formatted(categoryId);

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.location").value("Existing location"));
    }

    @Test
    void blankLocationClearsExistingLocation() throws Exception {
        long categoryId = firstCategoryId();
        ServiceRequest request = persist("Clear location", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        request.setLocation("Old location");
        serviceRequestRepository.saveAndFlush(request);

        String token = login("employee@servicehub.local", "Password123!");
        String body = """
                {"title":"Location cleared",
                 "description":"Updated description",
                 "categoryId":%d,
                 "location":""}
                """.formatted(categoryId);

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value((Object) null));
    }

    @Test
    void invalidBodyReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Invalid update", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
    }

    @Test
    void unknownCategoryReturnsNotFound() throws Exception {
        ServiceRequest request = persist("Unknown category", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        String body = """
                {"title":"Unknown category",
                 "description":"This category does not exist",
                 "categoryId":999999}
                """;

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUnknownRequestReturnsNotFound() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/requests/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Unknown request", firstCategoryId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithoutTokenReturnsUnauthorized() throws Exception {
        ServiceRequest request = persist("No token update", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);

        mockMvc.perform(put("/api/requests/{id}", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("No token", firstCategoryId())))
                .andExpect(status().isUnauthorized());
    }

    private long firstCategoryId() {
        return categoryRepository.findAll(Sort.by("id")).getFirst().getId();
    }

    private String validBody(String title, long categoryId) {
        return """
                {"title":"%s",
                 "description":"Updated description",
                 "categoryId":%d,
                 "priority":"HIGH",
                 "location":"Updated location"}
                """.formatted(title, categoryId);
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   Priority priority, RequestStatus status,
                                   String assignedToEmail) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Update fixture for " + title);
        request.setCategory(categoryRepository.findAll(Sort.by("id")).getFirst());
        request.setPriority(priority);
        request.setStatus(status);
        request.setRequester(requester);
        request.setDepartment(department);

        if (assignedToEmail != null) {
            request.setAssignedTo(
                    userRepository.findByEmailIgnoreCase(assignedToEmail).orElseThrow());
        }

        return serviceRequestRepository.saveAndFlush(request);
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
