package com.servicehub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ServiceRequestCommentListTest {

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
    void employeeSeesOnlyPublicCommentsOnOwnRequest() throws Exception {
        ServiceRequest request = persistRequest("Employee comment list",
                "employee@servicehub.local", 1L, null);
        persistComment(request, "employee@servicehub.local", "Public by employee", false);
        persistComment(request, "tech@servicehub.local", "Internal by technician", true);
        persistComment(request, "manager@servicehub.local", "Public by manager", false);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].content").value("Public by employee"))
                .andExpect(jsonPath("$.content[0].internal").value(false))
                .andExpect(jsonPath("$.content[0].author.email")
                        .value("employee@servicehub.local"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[1].content").value("Public by manager"))
                .andExpect(jsonPath("$.content[1].internal").value(false));
    }

    @Test
    void staffSeesInternalAndPublicComments() throws Exception {
        ServiceRequest request = persistRequest("Staff comment list",
                "employee@servicehub.local", 1L, null);
        persistComment(request, "employee@servicehub.local", "Public comment", false);
        persistComment(request, "tech@servicehub.local", "Internal work note", true);
        persistComment(request, "admin@servicehub.local", "Admin note", true);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[1].internal").value(true))
                .andExpect(jsonPath("$.content[1].content").value("Internal work note"))
                .andExpect(jsonPath("$.content[2].internal").value(true));
    }

    @Test
    void technicianSeesInternalNotesOnAssignedRequest() throws Exception {
        ServiceRequest request = persistRequest("Technician comment list",
                "employee@servicehub.local", 1L, "tech@servicehub.local");
        persistComment(request, "employee@servicehub.local", "Public comment", false);
        persistComment(request, "admin@servicehub.local", "Internal admin note", true);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].internal").value(true));
    }

    @Test
    void adminSeesAllCommentsOnAnyRequest() throws Exception {
        ServiceRequest request = persistRequest("Admin comment list",
                "employee@servicehub.local", 2L, null);
        persistComment(request, "employee@servicehub.local", "Public comment", false);
        persistComment(request, "tech@servicehub.local", "Internal work note", true);
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].internal").value(true));
    }

    @Test
    void employeeCannotListCommentsOnAnotherUsersRequest() throws Exception {
        ServiceRequest request = persistRequest("Employee forbidden comments",
                "tech@servicehub.local", 1L, null);
        persistComment(request, "tech@servicehub.local", "Public comment", false);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListDepartmentCommentsButNotOtherDepartment() throws Exception {
        ServiceRequest departmentRequest = persistRequest("Manager department comments",
                "employee@servicehub.local", 1L, null);
        ServiceRequest otherRequest = persistRequest("Manager other comments",
                "employee@servicehub.local", 2L, null);
        persistComment(departmentRequest, "tech@servicehub.local", "Department note", true);
        String token = login("manager@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", departmentRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/requests/{id}/comments", otherRequest.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotListCommentsOnUnassignedRequest() throws Exception {
        ServiceRequest request = persistRequest("Technician forbidden comments",
                "employee@servicehub.local", 1L, null);
        String token = login("tech@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void commentsArePaginatedInChronologicalOrder() throws Exception {
        ServiceRequest request = persistRequest("Paginated comments",
                "employee@servicehub.local", 1L, null);
        for (int i = 1; i <= 5; i++) {
            persistComment(request, "employee@servicehub.local", "Comment " + i, false);
        }
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].content").value("Comment 1"))
                .andExpect(jsonPath("$.content[1].content").value("Comment 2"));

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .param("page", "2")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("Comment 5"))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void requestWithoutCommentsReturnsEmptyPage() throws Exception {
        ServiceRequest request = persistRequest("No comments",
                "employee@servicehub.local", 1L, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        ServiceRequest request = persistRequest("Invalid pagination",
                "employee@servicehub.local", 1L, null);
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId())
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commentsForUnknownRequestReturnsNotFound() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/requests/{id}/comments", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCommentsWithoutTokenReturnsUnauthorized() throws Exception {
        ServiceRequest request = persistRequest("No token comment list",
                "employee@servicehub.local", 1L, null);

        mockMvc.perform(get("/api/requests/{id}/comments", request.getId()))
                .andExpect(status().isUnauthorized());
    }

    private ServiceRequest persistRequest(String title, String requesterEmail,
                                          Long departmentId, String assignedToEmail) {
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail).orElseThrow();
        Department department = departmentRepository.findById(departmentId).orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("T-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle(title);
        request.setDescription("Comment list fixture for " + title);
        request.setCategory(categoryRepository.findAll(Sort.by("id")).getFirst());
        request.setPriority(Priority.MEDIUM);
        request.setStatus(RequestStatus.SUBMITTED);
        request.setRequester(requester);
        request.setDepartment(department);

        if (assignedToEmail != null) {
            request.setAssignedTo(
                    userRepository.findByEmailIgnoreCase(assignedToEmail).orElseThrow());
        }

        return serviceRequestRepository.saveAndFlush(request);
    }

    private void persistComment(ServiceRequest request, String authorEmail, String content,
                                boolean internal) {
        RequestComment comment = new RequestComment();
        comment.setRequest(request);
        comment.setAuthor(userRepository.findByEmailIgnoreCase(authorEmail).orElseThrow());
        comment.setContent(content);
        comment.setInternal(internal);
        commentRepository.saveAndFlush(comment);
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
