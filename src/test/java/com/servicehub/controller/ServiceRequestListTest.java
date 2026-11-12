package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
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
class ServiceRequestListTest {

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
    void employeeSeesOnlyOwnRequests() throws Exception {
        String tag = tag();
        ServiceRequest ownOne = persist(tag + " own one", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest ownTwo = persist(tag + " own two", "employee@servicehub.local", 2L,
                Priority.HIGH, RequestStatus.PENDING, null);
        persist(tag + " someone else", "tech@servicehub.local", 1L,
                Priority.LOW, RequestStatus.SUBMITTED, null);

        String token = login("employee@servicehub.local", "Password123!");
        String response = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(response)).containsExactlyInAnyOrder(ownOne.getId(), ownTwo.getId());
        assertThat(JsonPath.<List<String>>read(response, "$.content[*].requester.email"))
                .containsOnly("employee@servicehub.local");
    }

    @Test
    void technicianSeesOwnAndAssignedRequests() throws Exception {
        String tag = tag();
        ServiceRequest own = persist(tag + " own", "tech@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest assigned = persist(tag + " assigned", "employee@servicehub.local", 1L,
                Priority.HIGH, RequestStatus.ASSIGNED, "tech@servicehub.local");
        persist(tag + " unassigned", "employee@servicehub.local", 1L,
                Priority.LOW, RequestStatus.SUBMITTED, null);

        String token = login("tech@servicehub.local", "Password123!");
        String response = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(response)).containsExactlyInAnyOrder(own.getId(), assigned.getId());
    }

    @Test
    void managerSeesOwnAndDepartmentRequestsOnly() throws Exception {
        String tag = tag();
        ServiceRequest departmentRequest = persist(tag + " department", "employee@servicehub.local",
                1L, Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest own = persist(tag + " own", "manager@servicehub.local", 2L,
                Priority.HIGH, RequestStatus.SUBMITTED, null);
        persist(tag + " other department", "employee@servicehub.local", 2L,
                Priority.LOW, RequestStatus.SUBMITTED, null);

        String token = login("manager@servicehub.local", "Password123!");
        String response = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(response)).containsExactlyInAnyOrder(
                departmentRequest.getId(), own.getId());
    }

    @Test
    void adminSeesAllRequests() throws Exception {
        String tag = tag();
        ServiceRequest first = persist(tag + " one", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest second = persist(tag + " two", "tech@servicehub.local", 2L,
                Priority.HIGH, RequestStatus.IN_PROGRESS, null);
        ServiceRequest third = persist(tag + " three", "manager@servicehub.local", 3L,
                Priority.CRITICAL, RequestStatus.PENDING, null);

        String token = login("admin@servicehub.local", "Password123!");
        String response = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(response)).containsExactlyInAnyOrder(
                first.getId(), second.getId(), third.getId());
    }

    @Test
    void filtersByStatusPriorityDepartmentAndAssignee() throws Exception {
        String tag = tag();
        ServiceRequest pendingHigh = persist(tag + " pending high", "employee@servicehub.local",
                1L, Priority.HIGH, RequestStatus.PENDING, null);
        persist(tag + " pending low", "employee@servicehub.local",
                1L, Priority.LOW, RequestStatus.PENDING, null);
        persist(tag + " submitted high", "employee@servicehub.local",
                1L, Priority.HIGH, RequestStatus.SUBMITTED, null);
        ServiceRequest otherDepartment = persist(tag + " other department",
                "employee@servicehub.local", 2L, Priority.CRITICAL, RequestStatus.PENDING, null);
        ServiceRequest assigned = persist(tag + " assigned", "employee@servicehub.local",
                1L, Priority.MEDIUM, RequestStatus.ASSIGNED, "tech@servicehub.local");

        String token = login("admin@servicehub.local", "Password123!");
        long technicianId = userRepository.findByEmailIgnoreCase("tech@servicehub.local")
                .orElseThrow().getId();

        String statusAndPriority = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .param("status", "pending")
                        .param("priority", "high")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(statusAndPriority)).containsExactly(pendingHigh.getId());

        String byDepartment = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .param("departmentId", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(byDepartment)).containsExactly(otherDepartment.getId());

        String byAssignee = mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .param("assignedToId", String.valueOf(technicianId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(byAssignee)).containsExactly(assigned.getId());
    }

    @Test
    void supportsPaginationAndSorting() throws Exception {
        String tag = tag();
        ServiceRequest first = persist(tag + " first", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);
        ServiceRequest second = persist(tag + " second", "employee@servicehub.local", 1L,
                Priority.MEDIUM, RequestStatus.SUBMITTED, null);

        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .param("size", "1")
                        .param("sort", "id")
                        .param("direction", "desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.getId().intValue()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/requests")
                        .param("search", tag)
                        .param("size", "1")
                        .param("page", "1")
                        .param("sort", "id")
                        .param("direction", "desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId().intValue()))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidStatusFilterReturnsBadRequest() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests")
                        .param("status", "URGENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPageSizeReturnsBadRequest() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/requests"))
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
        request.setDescription("Visibility fixture for " + title);
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

    private List<Long> ids(String response) {
        List<Number> ids = JsonPath.read(response, "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private String tag() {
        return "vis-" + UUID.randomUUID().toString().substring(0, 8);
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
