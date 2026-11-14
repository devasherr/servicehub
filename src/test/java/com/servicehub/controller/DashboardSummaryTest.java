package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.Role;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardSummaryTest {

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
    void adminSummaryReturnsRequiredTotalsAndBreakdowns() throws Exception {
        Department department = newDepartment();
        List<ServiceCategory> categories = categories();
        ServiceCategory firstCategory = categories.getFirst();
        ServiceCategory secondCategory = categories.get(1);
        User technician = user("tech@servicehub.local");

        persist("Dashboard submitted", department, firstCategory, Priority.HIGH,
                RequestStatus.SUBMITTED, null, null);
        persist("Dashboard pending", department, secondCategory, Priority.CRITICAL,
                RequestStatus.PENDING, null, null);
        persist("Dashboard in progress", department, firstCategory, Priority.CRITICAL,
                RequestStatus.IN_PROGRESS, technician, null);
        persist("Dashboard resolved", department, firstCategory, Priority.MEDIUM,
                RequestStatus.RESOLVED, technician, Instant.now().plusSeconds(7200));
        persist("Dashboard closed", department, secondCategory, Priority.LOW,
                RequestStatus.CLOSED, technician, Instant.now().plusSeconds(14400));

        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", department.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(5))
                .andExpect(jsonPath("$.pendingRequests").value(1))
                .andExpect(jsonPath("$.inProgressRequests").value(1))
                .andExpect(jsonPath("$.resolvedRequests").value(1))
                .andExpect(jsonPath("$.criticalRequests").value(2))
                .andExpect(jsonPath("$.requestsByStatus.SUBMITTED").value(1))
                .andExpect(jsonPath("$.requestsByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.requestsByStatus.ASSIGNED").value(0))
                .andExpect(jsonPath("$.requestsByStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.requestsByStatus.RESOLVED").value(1))
                .andExpect(jsonPath("$.requestsByStatus.CLOSED").value(1))
                .andExpect(jsonPath("$.requestsByPriority.LOW").value(1))
                .andExpect(jsonPath("$.requestsByPriority.MEDIUM").value(1))
                .andExpect(jsonPath("$.requestsByPriority.HIGH").value(1))
                .andExpect(jsonPath("$.requestsByPriority.CRITICAL").value(2))
                .andExpect(jsonPath("$.requestsByCategory.length()").value(2))
                .andExpect(jsonPath("$.requestsByCategory[0].categoryId")
                        .value(firstCategory.getId().intValue()))
                .andExpect(jsonPath("$.requestsByCategory[0].requestCount").value(3))
                .andExpect(jsonPath("$.requestsByCategory[1].categoryId")
                        .value(secondCategory.getId().intValue()))
                .andExpect(jsonPath("$.requestsByCategory[1].requestCount").value(2))
                .andExpect(jsonPath("$.technicianWorkload.length()").value(1))
                .andExpect(jsonPath("$.technicianWorkload[0].technicianId")
                        .value(technician.getId().intValue()))
                .andExpect(jsonPath("$.technicianWorkload[0].email")
                        .value("tech@servicehub.local"))
                .andExpect(jsonPath("$.technicianWorkload[0].assignedRequests").value(3))
                .andExpect(jsonPath("$.technicianWorkload[0].inProgressRequests").value(1))
                .andExpect(jsonPath("$.technicianWorkload[0].resolvedRequests").value(1))
                .andExpect(jsonPath("$.averageResolutionTimeHours").value(3.0));
    }

    @Test
    void summarySupportsPriorityStatusDateAndDepartmentFilters() throws Exception {
        Department department = newDepartment();
        List<ServiceCategory> categories = categories();
        User technician = user("tech@servicehub.local");

        persist("Filter pending", department, categories.getFirst(), Priority.CRITICAL,
                RequestStatus.PENDING, null, null);
        persist("Filter in progress", department, categories.getFirst(), Priority.CRITICAL,
                RequestStatus.IN_PROGRESS, technician, null);
        persist("Filter resolved", department, categories.get(1), Priority.MEDIUM,
                RequestStatus.RESOLVED, technician, Instant.now().plusSeconds(7200));

        String token = login("admin@servicehub.local", "Password123!");
        String departmentId = department.getId().toString();
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("priority", "CRITICAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(2))
                .andExpect(jsonPath("$.criticalRequests").value(2))
                .andExpect(jsonPath("$.pendingRequests").value(1))
                .andExpect(jsonPath("$.requestsByPriority.CRITICAL").value(2))
                .andExpect(jsonPath("$.requestsByPriority.MEDIUM").value(0))
                .andExpect(jsonPath("$.averageResolutionTimeHours").value((Object) null));

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("status", "IN_PROGRESS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.inProgressRequests").value(1));

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("status", "RESOLVED")
                        .param("priority", "MEDIUM")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.averageResolutionTimeHours").value(2.0));

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("dateFrom", today)
                        .param("dateTo", today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(3));

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("dateFrom", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0));

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", departmentId)
                        .param("dateTo", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0));
    }

    @Test
    void emptyScopeReturnsZeroedSummary() throws Exception {
        Department department = newDepartment();
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", department.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0))
                .andExpect(jsonPath("$.pendingRequests").value(0))
                .andExpect(jsonPath("$.inProgressRequests").value(0))
                .andExpect(jsonPath("$.resolvedRequests").value(0))
                .andExpect(jsonPath("$.criticalRequests").value(0))
                .andExpect(jsonPath("$.requestsByStatus.SUBMITTED").value(0))
                .andExpect(jsonPath("$.requestsByStatus.CLOSED").value(0))
                .andExpect(jsonPath("$.requestsByPriority.CRITICAL").value(0))
                .andExpect(jsonPath("$.requestsByCategory").isEmpty())
                .andExpect(jsonPath("$.technicianWorkload").isEmpty())
                .andExpect(jsonPath("$.averageResolutionTimeHours").value((Object) null));
    }

    @Test
    void managerSummaryIsScopedToOwnDepartment() throws Exception {
        Department department = departmentRepository.findById(1L).orElseThrow();
        Department otherDepartment = departmentRepository.findById(2L).orElseThrow();
        List<ServiceCategory> categories = categories();

        String managerToken = login("manager@servicehub.local", "Password123!");
        long baseline = totalRequests(managerToken);

        persist("Manager scope own", department, categories.getFirst(), Priority.HIGH,
                RequestStatus.SUBMITTED, null, null);
        persist("Manager scope other", otherDepartment, categories.getFirst(), Priority.HIGH,
                RequestStatus.SUBMITTED, null, null);

        assertThat(totalRequests(managerToken)).isEqualTo(baseline + 1);

        String adminToken = login("admin@servicehub.local", "Password123!");
        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", otherDepartment.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0));

        long adminOtherDepartment = ((Number) JsonPath.read(
                body(adminToken, "departmentId", otherDepartment.getId().toString()),
                "$.totalRequests")).longValue();
        assertThat(adminOtherDepartment).isGreaterThanOrEqualTo(1);
    }

    @Test
    void technicianSummaryIsScopedToAssignedWork() throws Exception {
        Department department = departmentRepository.findById(1L).orElseThrow();
        User technician = user("tech@servicehub.local");
        User otherTechnician = createTechnician(
                "dashboard.tech." + UUID.randomUUID() + "@servicehub.local");
        List<ServiceCategory> categories = categories();

        String technicianToken = login("tech@servicehub.local", "Password123!");
        long baseline = totalRequests(technicianToken);

        persist("Technician assigned", department, categories.getFirst(), Priority.HIGH,
                RequestStatus.ASSIGNED, technician, null);
        persist("Other technician assigned", department, categories.getFirst(), Priority.HIGH,
                RequestStatus.ASSIGNED, otherTechnician, null);
        persist("Technician own unassigned", department, categories.getFirst(), Priority.HIGH,
                RequestStatus.SUBMITTED, null, null, "tech@servicehub.local");

        assertThat(totalRequests(technicianToken)).isEqualTo(baseline + 1);
    }

    @Test
    void employeeCannotViewDashboard() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidFiltersReturnBadRequest() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("dateFrom", tomorrow)
                        .param("dateTo", today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("dateFrom", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("priority", "URGENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("status", "DONE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("departmentId", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private long totalRequests(String token) throws Exception {
        return ((Number) JsonPath.read(body(token), "$.totalRequests")).longValue();
    }

    private String body(String token, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/dashboard/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setName("Dashboard " + UUID.randomUUID());
        return departmentRepository.saveAndFlush(department);
    }

    private List<ServiceCategory> categories() {
        return categoryRepository.findAll(Sort.by("id"));
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
        technician.setFullName("Dashboard Technician");
        technician.setRole(role);
        technician.setDepartment(department);
        technician.setActive(true);
        return userRepository.saveAndFlush(technician);
    }

    private ServiceRequest persist(String title, Department department, ServiceCategory category,
                                   Priority priority, RequestStatus status, User assignedTo,
                                   Instant resolvedAt) {
        return persist(title, department, category, priority, status, assignedTo, resolvedAt,
                "employee@servicehub.local");
    }

    private ServiceRequest persist(String title, Department department, ServiceCategory category,
                                   Priority priority, RequestStatus status, User assignedTo,
                                   Instant resolvedAt, String requesterEmail) {
        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("D-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Dashboard fixture for " + title);
        request.setCategory(category);
        request.setPriority(priority);
        request.setStatus(status);
        request.setRequester(user(requesterEmail));
        request.setDepartment(department);
        request.setAssignedTo(assignedTo);
        request.setResolvedAt(resolvedAt);
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
