package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.RequestStatusHistory;
import com.servicehub.entity.Role;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.RequestStatusHistoryRepository;
import com.servicehub.repository.RoleRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.repository.UserRepository;
import java.time.Instant;
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
class ServiceRequestStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private RequestStatusHistoryRepository statusHistoryRepository;

    @Test
    void adminCanMoveSubmittedRequestToPending() throws Exception {
        ServiceRequest request = persist("Submitted to pending", "employee@servicehub.local",
                1L, RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"note\":\"Reviewing request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resolvedAt").value((Object) null))
                .andExpect(jsonPath("$.closedAt").value((Object) null));

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(history.getFirst().getChangedBy().getEmail())
                .isEqualTo("admin@servicehub.local");
        assertThat(history.getFirst().getNote()).isEqualTo("Reviewing request");
    }

    @Test
    void technicianCanStartAssignedRequest() throws Exception {
        ServiceRequest request = persist("Start work", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"Started diagnostics\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(history.getFirst().getChangedBy().getEmail())
                .isEqualTo("tech@servicehub.local");
    }

    @Test
    void technicianCanResolveAssignedRequest() throws Exception {
        ServiceRequest request = persist("Resolve work", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"note\":\"Replaced the power supply\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").value((Object) null));

        ServiceRequest saved = serviceRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getResolvedAt()).isNotNull().isBefore(Instant.now().plusSeconds(5));
        assertThat(saved.getClosedAt()).isNull();
    }

    @Test
    void managerCanApproveResolvedDepartmentRequest() throws Exception {
        Instant originalResolvedAt = Instant.parse("2026-01-02T03:04:05Z");
        ServiceRequest request = persist("Manager approval", "employee@servicehub.local", 1L,
                RequestStatus.RESOLVED, "tech@servicehub.local", originalResolvedAt);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"note\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        ServiceRequest saved = serviceRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getResolvedAt()).isEqualTo(originalResolvedAt);
        assertThat(saved.getClosedAt()).isNotNull();

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(history.getFirst().getChangedBy().getEmail())
                .isEqualTo("manager@servicehub.local");
    }

    @Test
    void adminCanCloseResolvedRequestAndSetsResolvedAtWhenMissing() throws Exception {
        ServiceRequest request = persist("Admin close", "employee@servicehub.local", 2L,
                RequestStatus.RESOLVED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
    }

    @Test
    void fullWorkflowRecordsEveryTransition() throws Exception {
        ServiceRequest request = persist("Full workflow", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String technicianToken = login("tech@servicehub.local", "Password123!");
        String managerToken = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"note\":\"Work complete\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(3);
        assertThat(history)
                .extracting(RequestStatusHistory::getFromStatus)
                .containsExactly(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                        RequestStatus.RESOLVED);
        assertThat(history)
                .extracting(RequestStatusHistory::getToStatus)
                .containsExactly(RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED,
                        RequestStatus.CLOSED);
        assertThat(history)
                .extracting(entry -> entry.getChangedBy().getEmail())
                .containsExactly("tech@servicehub.local", "tech@servicehub.local",
                        "manager@servicehub.local");
    }

    @Test
    void employeeCannotChangeStatus() throws Exception {
        ServiceRequest request = persist("Employee status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotChangeUnassignedRequest() throws Exception {
        ServiceRequest request = persist("Unassigned status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotChangeAnotherTechniciansRequest() throws Exception {
        User otherTechnician = createTechnician(
                "other.tech." + UUID.randomUUID() + "@servicehub.local");
        ServiceRequest request = persist("Other technician status", "employee@servicehub.local",
                1L, RequestStatus.ASSIGNED, otherTechnician.getEmail(), null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotCloseResolvedRequest() throws Exception {
        ServiceRequest request = persist("Technician close", "employee@servicehub.local", 1L,
                RequestStatus.RESOLVED, "tech@servicehub.local", Instant.now());
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotChangeOtherDepartmentStatus() throws Exception {
        ServiceRequest request = persist("Other department approval", "employee@servicehub.local",
                2L, RequestStatus.RESOLVED, null, Instant.now());
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotStartWork() throws Exception {
        ServiceRequest request = persist("Manager start work", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTransitionsReturnConflict() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");
        ServiceRequest submitted = persist("Invalid submitted jump", "employee@servicehub.local",
                1L, RequestStatus.SUBMITTED, null, null);
        ServiceRequest closed = persist("Closed terminal", "employee@servicehub.local", 1L,
                RequestStatus.CLOSED, null, null);

        mockMvc.perform(patch("/api/requests/{id}/status", submitted.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/requests/{id}/status", closed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());

        assertThat(history(submitted.getId())).isEmpty();
        assertThat(history(closed.getId())).isEmpty();
    }

    @Test
    void cannotSetAssignedThroughStatusEndpoint() throws Exception {
        ServiceRequest request = persist("Assigned via status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Use the assignment endpoint to assign a technician"));
    }

    @Test
    void missingStatusReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Missing status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"No status supplied\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void unknownStatusReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Unknown status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noteLongerThan500CharactersReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Long note", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");
        String body = "{\"status\":\"PENDING\",\"note\":\"%s\"}"
                .formatted("x".repeat(501));

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());
    }

    @Test
    void blankNoteIsStoredAsNull() throws Exception {
        ServiceRequest request = persist("Blank note", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"note\":\"   \"}"))
                .andExpect(status().isOk());

        assertThat(history(request.getId()).getFirst().getNote()).isNull();
    }

    @Test
    void changeStatusForUnknownRequestReturnsNotFound() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeStatusWithoutTokenReturnsUnauthorized() throws Exception {
        ServiceRequest request = persist("No token status", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isUnauthorized());
    }

    private List<RequestStatusHistory> history(Long requestId) {
        return statusHistoryRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    private User createTechnician(String email) {
        User existing = userRepository.findByEmailIgnoreCase("tech@servicehub.local").orElseThrow();
        Role role = roleRepository.findByName("TECHNICIAN").orElseThrow();
        Department department = departmentRepository.findById(1L).orElseThrow();

        User technician = new User();
        technician.setEmail(email);
        technician.setPasswordHash(existing.getPasswordHash());
        technician.setFullName("Other Technician");
        technician.setRole(role);
        technician.setDepartment(department);
        technician.setActive(true);
        return userRepository.saveAndFlush(technician);
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   RequestStatus status, String assignedToEmail,
                                   Instant resolvedAt) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Status fixture for " + title);
        request.setCategory(categoryRepository.findAll(Sort.by("id")).getFirst());
        request.setPriority(Priority.MEDIUM);
        request.setStatus(status);
        request.setRequester(requester);
        request.setDepartment(department);
        request.setResolvedAt(resolvedAt);

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
