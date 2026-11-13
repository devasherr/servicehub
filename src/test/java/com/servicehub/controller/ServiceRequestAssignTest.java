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
class ServiceRequestAssignTest {

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
    void adminCanAssignTechnicianAndStatusBecomesAssigned() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("Assign by admin", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo.id").value(technician.getId().intValue()))
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));

        ServiceRequest saved = serviceRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getAssignedTo().getId()).isEqualTo(technician.getId());
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.ASSIGNED);

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(history.getFirst().getChangedBy().getEmail())
                .isEqualTo("admin@servicehub.local");
        assertThat(history.getFirst().getNote()).contains("Tom Technician");
    }

    @Test
    void managerCanAssignDepartmentRequest() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("Assign by manager", "employee@servicehub.local", 1L,
                Priority.HIGH, RequestStatus.PENDING, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));

        List<RequestStatusHistory> history = history(request.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(history.getFirst().getToStatus()).isEqualTo(RequestStatus.ASSIGNED);
    }

    @Test
    void managerCannotAssignOtherDepartmentRequest() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("Manager other department", "employee@servicehub.local",
                2L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotAssign() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("Employee cannot assign", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotAssign() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("Technician cannot assign", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.ASSIGNED, "tech@servicehub.local");
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assigningNonTechnicianReturnsBadRequest() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@servicehub.local")
                .orElseThrow();
        ServiceRequest request = persist("Non technician", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(employee.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningInactiveTechnicianReturnsBadRequest() throws Exception {
        User technician = technician();
        technician.setActive(false);
        userRepository.saveAndFlush(technician);

        ServiceRequest request = persist("Inactive technician", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningUnknownTechnicianReturnsNotFound() throws Exception {
        ServiceRequest request = persist("Unknown technician", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void assigningUnknownRequestReturnsNotFound() throws Exception {
        User technician = technician();
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTechnicianIdReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Missing technician", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.technicianId").exists());
    }

    @Test
    void cannotAssignResolvedOrClosedRequest() throws Exception {
        User technician = technician();
        ServiceRequest resolved = persist("Resolved request", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.RESOLVED, null);
        ServiceRequest closed = persist("Closed request", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.CLOSED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", resolved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/requests/{id}/assign", closed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void canReassignToAnotherTechnician() throws Exception {
        User firstTechnician = technician();
        User secondTechnician = createTechnician(
                "second.tech." + UUID.randomUUID() + "@servicehub.local");
        ServiceRequest request = persist("Reassign request", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(firstTechnician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id").value(firstTechnician.getId().intValue()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(secondTechnician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id")
                        .value(secondTechnician.getId().intValue()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Reassignment does not change the status, so no extra history row is added.
        assertThat(history(request.getId())).hasSize(1);
    }

    @Test
    void assigningInProgressRequestKeepsInProgressStatus() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("In progress reassignment", "employee@servicehub.local",
                1L, Priority.HIGH, RequestStatus.IN_PROGRESS, "tech@servicehub.local");
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));

        assertThat(history(request.getId())).isEmpty();
    }

    @Test
    void assignWithoutTokenReturnsUnauthorized() throws Exception {
        User technician = technician();
        ServiceRequest request = persist("No token assign", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);

        mockMvc.perform(patch("/api/requests/{id}/assign", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technician.getId())))
                .andExpect(status().isUnauthorized());
    }

    private User technician() {
        return userRepository.findByEmailIgnoreCase("tech@servicehub.local").orElseThrow();
    }

    private User createTechnician(String email) {
        Role role = roleRepository.findByName("TECHNICIAN").orElseThrow();
        Department department = departmentRepository.findById(1L).orElseThrow();

        User technician = new User();
        technician.setEmail(email);
        technician.setPasswordHash("not-used");
        technician.setFullName("Second Technician");
        technician.setRole(role);
        technician.setDepartment(department);
        technician.setActive(true);
        return userRepository.saveAndFlush(technician);
    }

    private List<RequestStatusHistory> history(Long requestId) {
        return statusHistoryRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    private String assignBody(Long technicianId) {
        return """
                {"technicianId":%d}
                """.formatted(technicianId);
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   Priority priority, RequestStatus status,
                                   String assignedToEmail) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Assignment fixture for " + title);
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
