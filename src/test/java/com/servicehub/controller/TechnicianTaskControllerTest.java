package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.RequestStatusHistory;
import com.servicehub.entity.Role;
import com.servicehub.entity.ServiceCategory;
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
class TechnicianTaskControllerTest {

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
    void technicianCanStartAssignedTaskToInProgress() throws Exception {
        ServiceRequest request = persist("Start Task", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"Starting diagnostic work\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(history.getFirst().getChangedBy().getEmail()).isEqualTo("tech@servicehub.local");
        assertThat(history.getFirst().getNote()).isEqualTo("Starting diagnostic work");
    }

    @Test
    void technicianCanStartAssignedTaskUsingInprogressString() throws Exception {
        ServiceRequest request = persist("Start Task inprogress", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"inprogress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void technicianCanCompleteTaskToCompleted() throws Exception {
        ServiceRequest request = persist("Complete Task", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"note\":\"Replaced broken cable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").value((Object) null));

        ServiceRequest saved = serviceRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(saved.getResolvedAt()).isNotNull();

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(history.getFirst().getNote()).isEqualTo("Replaced broken cable");
    }

    @Test
    void technicianCanCompleteTaskUsingCompletedLowercase() throws Exception {
        ServiceRequest request = persist("Complete Task lowercase", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void technicianCanCompleteTaskToResolved() throws Exception {
        ServiceRequest request = persist("Resolve Task", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"note\":\"Work done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void putEndpointAlsoUpdatesStatus() throws Exception {
        ServiceRequest request = persist("PUT Task", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(put("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void aliasEndpointTasksStatusWorks() throws Exception {
        ServiceRequest request = persist("Alias /api/tasks", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void aliasEndpointTechnicianRequestsStatusWorks() throws Exception {
        ServiceRequest request = persist("Alias /api/technician/requests", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void existingEndpointAcceptsCompleted() throws Exception {
        ServiceRequest request = persist("Existing endpoint completed", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void technicianCannotUpdateUnassignedTask() throws Exception {
        ServiceRequest request = persist("Unassigned Task", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotUpdateOtherTechniciansTask() throws Exception {
        User otherTech = createTechnician("other.tech." + UUID.randomUUID() + "@servicehub.local");
        ServiceRequest request = persist("Other Tech Task", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, otherTech.getEmail(), null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotUpdateTaskStatus() throws Exception {
        ServiceRequest request = persist("Emp cannot update", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotCallTechnicianEndpoint() throws Exception {
        ServiceRequest request = persist("Mgr cannot call tech endpoint", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/api/technician/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void technicianCannotDirectlyCompleteFromAssignedWithoutStarting() throws Exception {
        ServiceRequest request = persist("Direct complete attempt", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void technicianCannotSetStatusToClosedOrSubmitted() throws Exception {
        ServiceRequest request = persist("Invalid status attempt", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/technician/tasks/{id}/status", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void technicianCanListAssignedTasks() throws Exception {
        ServiceRequest myTask = persist("My Task", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        User otherTech = createTechnician("other.tech." + UUID.randomUUID() + "@servicehub.local");
        persist("Other Task", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, otherTech.getEmail(), null);

        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/technician/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].assignedTo.email").value("tech@servicehub.local"));
    }

    @Test
    void technicianCanGetAssignedTaskById() throws Exception {
        ServiceRequest request = persist("Get task by id", "employee@servicehub.local", 1L,
                RequestStatus.ASSIGNED, "tech@servicehub.local", null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/technician/tasks/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.title").value("Get task by id"));
    }

    private User user(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private User createTechnician(String email) {
        User existing = user("tech@servicehub.local");
        Role role = roleRepository.findByName("TECHNICIAN").orElseThrow();
        Department department = departmentRepository.findById(1L).orElseThrow();

        User technician = new User();
        technician.setEmail(email);
        technician.setPasswordHash(existing.getPasswordHash());
        technician.setFullName("Another Technician");
        technician.setRole(role);
        technician.setDepartment(department);
        technician.setActive(true);
        return userRepository.saveAndFlush(technician);
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   RequestStatus status, String assignedToEmail, Instant resolvedAt) {
        Department department = departmentRepository.findById(departmentId).orElseThrow();
        ServiceCategory category = categoryRepository.findAll(Sort.by("id")).getFirst();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("TASK-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Task description for " + title);
        request.setCategory(category);
        request.setPriority(Priority.MEDIUM);
        request.setStatus(status);
        request.setRequester(user(requesterEmail));
        request.setDepartment(department);
        request.setAssignedTo(assignedToEmail == null ? null : user(assignedToEmail));
        request.setResolvedAt(resolvedAt);
        return serviceRequestRepository.saveAndFlush(request);
    }

    private List<RequestStatusHistory> history(Long requestId) {
        return statusHistoryRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    private String login(String email, String password) throws Exception {
        String requestBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }
}
