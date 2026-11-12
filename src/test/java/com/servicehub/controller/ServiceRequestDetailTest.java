package com.servicehub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.repository.UserRepository;
import java.util.UUID;
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
class ServiceRequestDetailTest {

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
    void creatorCanViewOwnRequest() throws Exception {
        ServiceRequest request = persist("Detail own request", "employee@servicehub.local", 1L,
                Priority.HIGH, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.referenceNumber").value(request.getReferenceNumber()))
                .andExpect(jsonPath("$.title").value("Detail own request"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.department.id").value(1))
                .andExpect(jsonPath("$.category.id").value(1));
    }

    @Test
    void adminCanViewAnyRequest() throws Exception {
        ServiceRequest request = persist("Admin visible request", "employee@servicehub.local", 2L,
                Priority.LOW, RequestStatus.PENDING, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().intValue()))
                .andExpect(jsonPath("$.requester.email").value("employee@servicehub.local"));
    }

    @Test
    void managerCanViewDepartmentAndOwnRequests() throws Exception {
        ServiceRequest departmentRequest = persist("Manager department request",
                "employee@servicehub.local", 1L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest ownOutsideDepartment = persist("Manager own request",
                "manager@servicehub.local", 2L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", departmentRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/{id}", ownOutsideDepartment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void managerCannotViewAnotherDepartmentRequest() throws Exception {
        ServiceRequest request = persist("Other department request", "employee@servicehub.local",
                2L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCanViewAssignedRequest() throws Exception {
        ServiceRequest assigned = persist("Assigned to technician", "employee@servicehub.local",
                1L, Priority.HIGH, RequestStatus.ASSIGNED, "tech@servicehub.local");
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", assigned.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.email").value("tech@servicehub.local"));
    }

    @Test
    void technicianCannotViewUnassignedRequest() throws Exception {
        ServiceRequest request = persist("Unassigned request", "employee@servicehub.local",
                1L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotViewAnotherEmployeesRequest() throws Exception {
        ServiceRequest request = persist("Technician own request", "tech@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRequestReturnsNotFound() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidIdReturnsBadRequest() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").exists());

        mockMvc.perform(get("/api/requests/{id}", 0)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailWithoutTokenReturnsUnauthorized() throws Exception {
        ServiceRequest request = persist("No token request", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);

        mockMvc.perform(get("/api/requests/{id}", request.getId()))
                .andExpect(status().isUnauthorized());
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   Priority priority, RequestStatus status,
                                   String assignedToEmail) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Detail fixture for " + title);
        request.setCategory(categoryRepository.findAll().getFirst());
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
