package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.Department;
import com.servicehub.entity.RequestComment;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.RequestCommentRepository;
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
class ServiceRequestCommentTest {

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

    @Autowired
    private RequestCommentRepository commentRepository;

    @Test
    void employeeCanAddPublicCommentToOwnRequest() throws Exception {
        ServiceRequest request = persist("Employee comment", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        User employee = userRepository.findByEmailIgnoreCase("employee@servicehub.local")
                .orElseThrow();
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  Please provide an update  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.requestId").value(request.getId().intValue()))
                .andExpect(jsonPath("$.content").value("Please provide an update"))
                .andExpect(jsonPath("$.internal").value(false))
                .andExpect(jsonPath("$.author.id").value(employee.getId().intValue()))
                .andExpect(jsonPath("$.author.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        List<RequestComment> comments = comments(request.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().getContent()).isEqualTo("Please provide an update");
        assertThat(comments.getFirst().isInternal()).isFalse();
        assertThat(comments.getFirst().getAuthor().getEmail())
                .isEqualTo("employee@servicehub.local");
    }

    @Test
    void employeeCanCommentOnOwnClosedRequest() throws Exception {
        ServiceRequest request = persist("Closed comment", "employee@servicehub.local", 1L,
                RequestStatus.CLOSED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Thanks for the help\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(false));

        assertThat(comments(request.getId())).hasSize(1);
    }

    @Test
    void employeeCannotCommentOnAnotherUsersRequest() throws Exception {
        ServiceRequest request = persist("Employee forbidden", "tech@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed\"}"))
                .andExpect(status().isForbidden());

        assertThat(comments(request.getId())).isEmpty();
    }

    @Test
    void managerCanCommentOnDepartmentRequestButNotOtherDepartment() throws Exception {
        ServiceRequest departmentRequest = persist("Manager comment", "employee@servicehub.local",
                1L, RequestStatus.SUBMITTED, null);
        ServiceRequest otherRequest = persist("Manager other comment", "employee@servicehub.local",
                2L, RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", departmentRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Department update\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/requests/{id}/comments", otherRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed\"}"))
                .andExpect(status().isForbidden());

        assertThat(comments(departmentRequest.getId())).hasSize(1);
        assertThat(comments(otherRequest.getId())).isEmpty();
    }

    @Test
    void technicianCanAddInternalWorkNoteToAssignedRequest() throws Exception {
        ServiceRequest request = persist("Work note", "employee@servicehub.local", 1L,
                RequestStatus.IN_PROGRESS, "tech@servicehub.local");
        User technician = userRepository.findByEmailIgnoreCase("tech@servicehub.local")
                .orElseThrow();
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Motherboard replacement required\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content")
                        .value("Motherboard replacement required"))
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.author.email").value("tech@servicehub.local"));

        List<RequestComment> comments = comments(request.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().isInternal()).isTrue();
        assertThat(comments.getFirst().getAuthor().getId()).isEqualTo(technician.getId());
    }

    @Test
    void technicianCannotCommentOnUnassignedRequest() throws Exception {
        ServiceRequest request = persist("Technician forbidden", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCommentOnAnyRequest() throws Exception {
        ServiceRequest request = persist("Admin comment", "employee@servicehub.local", 2L,
                RequestStatus.RESOLVED, null);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Closing this out\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.author.email").value("admin@servicehub.local"));
    }

    @Test
    void employeeCannotAddInternalNote() throws Exception {
        ServiceRequest request = persist("Employee internal", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Secret note\",\"internal\":true}"))
                .andExpect(status().isForbidden());

        assertThat(comments(request.getId())).isEmpty();
    }

    @Test
    void managerCanAddInternalNoteToDepartmentRequest() throws Exception {
        ServiceRequest request = persist("Manager internal", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Internal triage note\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));
    }

    @Test
    void blankContentReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Blank comment", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());

        assertThat(comments(request.getId())).isEmpty();
    }

    @Test
    void missingContentReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Missing comment", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void contentLongerThan2000CharactersReturnsBadRequest() throws Exception {
        ServiceRequest request = persist("Long comment", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);
        String token = login("employee@servicehub.local", "Password123!");
        String body = "{\"content\":\"%s\"}".formatted("x".repeat(2001));

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void commentOnUnknownRequestReturnsNotFound() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(post("/api/requests/{id}/comments", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Missing request\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addCommentWithoutTokenReturnsUnauthorized() throws Exception {
        ServiceRequest request = persist("No token comment", "employee@servicehub.local", 1L,
                RequestStatus.SUBMITTED, null);

        mockMvc.perform(post("/api/requests/{id}/comments", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"No token\"}"))
                .andExpect(status().isUnauthorized());
    }

    private List<RequestComment> comments(Long requestId) {
        return commentRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    private ServiceRequest persist(String title, String requesterEmail, Long departmentId,
                                   RequestStatus status, String assignedToEmail) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Comment fixture for " + title);
        request.setCategory(categoryRepository.findAll(Sort.by("id")).getFirst());
        request.setPriority(Priority.MEDIUM);
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
