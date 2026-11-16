package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private ServiceRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    void adminCanCreateCategory() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        String name = "Category " + UUID.randomUUID().toString().substring(0, 8);
        String payload = """
                {
                    "name": "%s",
                    "description": "Category for testing",
                    "active": true
                }
                """.formatted(name);

        String response = mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value("Category for testing"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        assertThat(categoryRepository.findById(id)).isPresent();
    }

    @Test
    void createCategoryWithDuplicateNameFailsWithConflict() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        // "Hardware" already seeded in migration V5
        String payload = """
                {
                    "name": "hardware",
                    "description": "Duplicate hardware"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void createCategoryValidationFailsOnBlankName() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        String payload = """
                {
                    "name": "   ",
                    "description": "Blank name"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotCreateCategory() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");
        String payload = """
                {
                    "name": "Employee Category"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateCategory() throws Exception {
        String payload = """
                {
                    "name": "Anonymous Category"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyAuthenticatedUserCanGetCategoryById() throws Exception {
        ServiceCategory category = categoryRepository.findByNameIgnoreCase("Hardware").orElseThrow();
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.name").value("Hardware"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getCategoryByIdNotFoundReturns404() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/categories/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCategoriesPaginated() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/categories")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void listCategoriesAllForDropdown() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        // Test /api/categories/all
        mockMvc.perform(get("/api/categories/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$[0].name").exists());

        // Test /api/categories?all=true
        mockMvc.perform(get("/api/categories")
                        .param("all", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void listCategoriesWithSearchFilter() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/categories")
                        .param("search", "hard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Hardware"));
    }

    @Test
    void adminCanUpdateCategory() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("Old Name", "Old Desc", true);

        String updatePayload = """
                {
                    "name": "Updated Name",
                    "description": "Updated Desc",
                    "active": false
                }
                """;

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated Desc"))
                .andExpect(jsonPath("$.active").value(false));

        ServiceCategory reloaded = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Updated Name");
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void updateCategoryDuplicateNameReturnsConflict() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("Unique Name", "Desc", true);

        // Hardware already exists
        String updatePayload = """
                {
                    "name": "Hardware",
                    "description": "Trying to rename to Hardware"
                }
                """;

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanPatchCategory() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("Patch Me", "Initial Desc", true);

        String patchPayload = """
                {
                    "active": false
                }
                """;

        mockMvc.perform(patch("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.name").value("Patch Me"))
                .andExpect(jsonPath("$.active").value(false));

        ServiceCategory reloaded = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getName()).isEqualTo("Patch Me");
    }

    @Test
    void adminCanDeleteUnusedCategory() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("Unused Category", "No requests", true);

        mockMvc.perform(delete("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    @Test
    void adminCannotDeleteCategoryInUseByRequest() throws Exception {
        String adminToken = login("admin@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("In Use Cat", "Has request", true);

        // Create a request using this category
        Department dept = departmentRepository.findAll().getFirst();
        User user = userRepository.findByEmailIgnoreCase("employee@servicehub.local").orElseThrow();

        ServiceRequest request = new ServiceRequest();
        request.setReferenceNumber("CAT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        request.setTitle("Request with category");
        request.setDescription("Testing category deletion restraint");
        request.setCategory(category);
        request.setPriority(Priority.MEDIUM);
        request.setStatus(RequestStatus.SUBMITTED);
        request.setRequester(user);
        request.setDepartment(dept);
        requestRepository.saveAndFlush(request);

        mockMvc.perform(delete("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void nonAdminCannotDeleteCategory() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");
        ServiceCategory category = createCategory("To Delete", "Desc", true);

        mockMvc.perform(delete("/api/categories/" + category.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aliasServiceCategoriesPathWorks() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/service-categories/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private ServiceCategory createCategory(String name, String description, boolean active) {
        ServiceCategory category = new ServiceCategory();
        category.setName(name);
        category.setDescription(description);
        category.setActive(active);
        return categoryRepository.saveAndFlush(category);
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
