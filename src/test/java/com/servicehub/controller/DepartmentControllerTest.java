package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private ServiceRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminCanCreateDepartment() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        String name = "Department " + UUID.randomUUID().toString().substring(0, 8);
        String payload = """
                {
                    "name": "%s"
                }
                """.formatted(name);

        String response = mockMvc.perform(post("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        assertThat(departmentRepository.findById(id)).isPresent();
    }

    @Test
    void createDepartmentWithDuplicateNameFailsWithConflict() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        // "Information Technology" already seeded in migration V10
        String payload = """
                {
                    "name": "information technology"
                }
                """;

        mockMvc.perform(post("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void createDepartmentValidationFailsOnBlankName() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        String payload = """
                {
                    "name": "   "
                }
                """;

        mockMvc.perform(post("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotCreateDepartment() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");
        String payload = """
                {
                    "name": "Employee Department"
                }
                """;

        mockMvc.perform(post("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateDepartment() throws Exception {
        String payload = """
                {
                    "name": "Anonymous Department"
                }
                """;

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyAuthenticatedUserCanGetDepartmentById() throws Exception {
        Department department = departmentRepository.findByNameIgnoreCase("Facilities").orElseThrow();
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(department.getId()))
                .andExpect(jsonPath("$.name").value("Facilities"));
    }

    @Test
    void getDepartmentByIdNotFoundReturns404() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/departments/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDepartmentsPaginated() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/departments")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void listDepartmentsAllForDropdown() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/departments/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[0].name").exists());

        mockMvc.perform(get("/api/departments")
                        .param("all", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/departments/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listDepartmentsWithSearchFilter() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/departments")
                        .param("search", "facil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Facilities"));
    }

    @Test
    void adminCanUpdateDepartment() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        Department department = createDepartment("Old Department");

        String updatePayload = """
                {
                    "name": "Updated Department"
                }
                """;

        mockMvc.perform(put("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(department.getId()))
                .andExpect(jsonPath("$.name").value("Updated Department"));

        Department reloaded = departmentRepository.findById(department.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Updated Department");
    }

    @Test
    void updateDepartmentDuplicateNameReturnsConflict() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        Department department = createDepartment("Unique Department");

        String updatePayload = """
                {
                    "name": "Information Technology"
                }
                """;

        mockMvc.perform(put("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanDeleteUnusedDepartment() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        Department department = createDepartment("Unused Department");

        mockMvc.perform(delete("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(departmentRepository.findById(department.getId())).isEmpty();
    }

    @Test
    void adminCannotDeleteDepartmentInUseByUser() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        Department department = departmentRepository.findByNameIgnoreCase("Information Technology").orElseThrow();

        mockMvc.perform(delete("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertThat(departmentRepository.findById(department.getId())).isPresent();
    }

    @Test
    void adminCannotDeleteDepartmentInUseByRequest() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        Department department = createDepartment("In Use Department");
        ServiceCategory category = categoryRepository.findByNameIgnoreCase("Hardware").orElseThrow();
        User user = userRepository.findByEmailIgnoreCase("employee@servicehub.local").orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("DEPT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle("Request with department");
        request.setDescription("Testing department deletion restraint");
        request.setCategory(category);
        request.setPriority(Priority.MEDIUM);
        request.setStatus(RequestStatus.SUBMITTED);
        request.setRequester(user);
        request.setDepartment(department);
        requestRepository.saveAndFlush(request);

        mockMvc.perform(delete("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertThat(departmentRepository.findById(department.getId())).isPresent();
    }

    @Test
    void nonAdminCannotDeleteDepartment() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");
        Department department = createDepartment("To Delete");

        mockMvc.perform(delete("/api/departments/" + department.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    private Department createDepartment(String name) {
        Department department = new Department();
        department.setName(name);
        return departmentRepository.saveAndFlush(department);
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
