package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.servicehub.repository.RoleRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class ServiceRequestStatusCountsTest {

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

    @Test
    void statusCountsReturnsExpectedTotalPendingInProgressAndCompleted() throws Exception {
        Department department = newDepartment();
        ServiceCategory category = categories().getFirst();
        User technician = user("tech@servicehub.local");

        // Create exactly 5 requests in isolated department:
        // 2 pending, 1 in progress, 2 completed (1 resolved + 1 closed)
        persist("Req 1 Pending", department, category, Priority.HIGH, RequestStatus.PENDING, null);
        persist("Req 2 Pending", department, category, Priority.MEDIUM, RequestStatus.PENDING, null);
        persist("Req 3 In Progress", department, category, Priority.CRITICAL, RequestStatus.IN_PROGRESS, technician);
        persist("Req 4 Resolved", department, category, Priority.LOW, RequestStatus.RESOLVED, technician);
        persist("Req 5 Closed", department, category, Priority.MEDIUM, RequestStatus.CLOSED, technician);

        String adminToken = login("admin@servicehub.local", "Password123!");

        // Test GET /api/requests/status-counts
        mockMvc.perform(get("/api/requests/status-counts")
                        .param("departmentId", department.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalRequests").value(5))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.pendingRequests").value(2))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.inProgressRequests").value(1))
                .andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.completedRequests").value(2))
                .andExpect(jsonPath("$.resolvedRequests").value(1))
                .andExpect(jsonPath("$.closedRequests").value(1))
                .andExpect(jsonPath("$.submittedRequests").value(0))
                .andExpect(jsonPath("$.assignedRequests").value(0))
                .andExpect(jsonPath("$.requestsByStatus.PENDING").value(2))
                .andExpect(jsonPath("$.requestsByStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.requestsByStatus.RESOLVED").value(1))
                .andExpect(jsonPath("$.requestsByStatus.CLOSED").value(1))
                .andExpect(jsonPath("$.requestsByStatus.SUBMITTED").value(0))
                .andExpect(jsonPath("$.byStatus.PENDING").value(2))
                .andExpect(jsonPath("$.statusCounts.length()").value(6));

        // Test alias GET /api/requests/counts
        mockMvc.perform(get("/api/requests/counts")
                        .param("departmentId", department.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.completed").value(2));

        // Test alias GET /api/dashboard/status-counts
        mockMvc.perform(get("/api/dashboard/status-counts")
                        .param("departmentId", department.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.completed").value(2));
    }

    @Test
    void employeeSeesOnlyOwnStatusCounts() throws Exception {
        Department department = newDepartment();
        ServiceCategory category = categories().getFirst();
        String tag = UUID.randomUUID().toString().substring(0, 8);

        persist(tag + " emp pending 1", department, category, Priority.MEDIUM, RequestStatus.PENDING,
                null, "employee@servicehub.local");
        persist(tag + " emp pending 2", department, category, Priority.HIGH, RequestStatus.PENDING,
                null, "employee@servicehub.local");
        persist(tag + " emp in progress", department, category, Priority.CRITICAL, RequestStatus.IN_PROGRESS,
                null, "employee@servicehub.local");
        persist(tag + " emp resolved 1", department, category, Priority.LOW, RequestStatus.RESOLVED,
                null, "employee@servicehub.local");
        persist(tag + " emp resolved 2", department, category, Priority.MEDIUM, RequestStatus.RESOLVED,
                null, "employee@servicehub.local");

        // Someone else's requests
        persist(tag + " other pending", department, category, Priority.MEDIUM, RequestStatus.PENDING,
                null, "admin@servicehub.local");
        persist(tag + " other in progress", department, category, Priority.MEDIUM, RequestStatus.IN_PROGRESS,
                null, "tech@servicehub.local");

        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/status-counts")
                        .param("search", tag)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalRequests").value(5))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.pendingRequests").value(2))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.inProgressRequests").value(1))
                .andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.completedRequests").value(2));
    }

    @Test
    void statusCountsSupportsFilters() throws Exception {
        Department department = newDepartment();
        List<ServiceCategory> cats = categories();
        ServiceCategory cat1 = cats.get(0);
        ServiceCategory cat2 = cats.get(1);

        persist("Filter High Pending", department, cat1, Priority.HIGH, RequestStatus.PENDING, null);
        persist("Filter Low Pending", department, cat2, Priority.LOW, RequestStatus.PENDING, null);
        persist("Filter High InProgress", department, cat1, Priority.HIGH, RequestStatus.IN_PROGRESS, null);

        String adminToken = login("admin@servicehub.local", "Password123!");
        String deptId = department.getId().toString();

        // Filter by priority
        mockMvc.perform(get("/api/requests/status-counts")
                        .param("departmentId", deptId)
                        .param("priority", "HIGH")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.completed").value(0));

        // Filter by category
        mockMvc.perform(get("/api/requests/status-counts")
                        .param("departmentId", deptId)
                        .param("categoryId", cat2.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pending").value(1));

        // Filter by date range
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/requests/status-counts")
                        .param("departmentId", deptId)
                        .param("dateFrom", today)
                        .param("dateTo", today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        // Future date gives 0
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        mockMvc.perform(get("/api/requests/status-counts")
                        .param("departmentId", deptId)
                        .param("dateFrom", tomorrow)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void invalidDateRangeReturnsBadRequest() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        mockMvc.perform(get("/api/requests/status-counts")
                        .param("dateFrom", tomorrow)
                        .param("dateTo", today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/requests/status-counts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/requests/counts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeForbiddenFromDashboardStatusCounts() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/dashboard/status-counts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setName("StatusCounts " + UUID.randomUUID());
        return departmentRepository.saveAndFlush(department);
    }

    private List<ServiceCategory> categories() {
        return categoryRepository.findAll(Sort.by("id"));
    }

    private User user(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private ServiceRequest persist(String title, Department department, ServiceCategory category,
                                   Priority priority, RequestStatus status, User assignedTo) {
        return persist(title, department, category, priority, status, assignedTo,
                "employee@servicehub.local");
    }

    private ServiceRequest persist(String title, Department department, ServiceCategory category,
                                   Priority priority, RequestStatus status, User assignedTo,
                                   String requesterEmail) {
        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("SC-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Description for " + title);
        request.setCategory(category);
        request.setPriority(priority);
        request.setStatus(status);
        request.setRequester(user(requesterEmail));
        request.setDepartment(department);
        request.setAssignedTo(assignedTo);
        if (status == RequestStatus.RESOLVED || status == RequestStatus.CLOSED) {
            request.setResolvedAt(Instant.now());
        }
        if (status == RequestStatus.CLOSED) {
            request.setClosedAt(Instant.now());
        }
        return serviceRequestRepository.saveAndFlush(request);
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
