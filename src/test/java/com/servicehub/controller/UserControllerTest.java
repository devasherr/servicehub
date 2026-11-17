package com.servicehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicehub.entity.User;
import com.servicehub.repository.UserRepository;
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getMeReturnsAuthenticatedUsersProfile() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.fullName").value("Eve Employee"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.department.id").value(1))
                .andExpect(jsonPath("$.department.name").value("Information Technology"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getMeWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/users")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCanFilterUsers() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/users")
                        .param("role", "technician")
                        .param("active", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("tech@servicehub.local"))
                .andExpect(jsonPath("$.content[0].role").value("TECHNICIAN"));
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingUsersWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPageSizeReturnsBadRequest() throws Exception {
        String token = login("admin@servicehub.local", "Password123!");

        mockMvc.perform(get("/api/users")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCanUpdateOwnFullNameAndPhone() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  Eve Updated  ","phone":"+10000000999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.fullName").value("Eve Updated"))
                .andExpect(jsonPath("$.phone").value("+10000000999"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmailIgnoreCase("employee@servicehub.local").orElseThrow();
        assertThat(saved.getFullName()).isEqualTo("Eve Updated");
        assertThat(saved.getPhone()).isEqualTo("+10000000999");
    }

    @Test
    void updateProfileOnlyChangesProvidedFieldsAndCanClearPhone() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");
        String profile = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String originalName = JsonPath.read(profile, "$.fullName");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(originalName))
                .andExpect(jsonPath("$.phone").doesNotExist());

        User saved = userRepository.findByEmailIgnoreCase("employee@servicehub.local").orElseThrow();
        assertThat(saved.getFullName()).isEqualTo(originalName);
        assertThat(saved.getPhone()).isNull();
    }

    @Test
    void updateProfileIgnoresUserIdAndProtectedFields() throws Exception {
        String employeeToken = login("employee@servicehub.local", "Password123!");
        String adminToken = login("admin@servicehub.local", "Password123!");
        String adminProfile = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int adminId = JsonPath.read(adminProfile, "$.id");
        String adminEmail = JsonPath.read(adminProfile, "$.email");
        String adminName = JsonPath.read(adminProfile, "$.fullName");

        String body = """
                {"id":%d,"userId":%d,"email":"intruder@example.com","role":"ADMIN",
                 "active":false,"fullName":"Updated Employee"}
                """.formatted(adminId, adminId);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee@servicehub.local"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fullName").value("Updated Employee"));

        User admin = userRepository.findById((long) adminId).orElseThrow();
        assertThat(admin.getEmail()).isEqualTo(adminEmail);
        assertThat(admin.getFullName()).isEqualTo(adminName);
        assertThat(admin.getRole().getName()).isEqualTo("ADMIN");
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void updateProfileWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nobody\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidProfileUpdateReturnsBadRequest() throws Exception {
        String token = login("employee@servicehub.local", "Password123!");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + "A".repeat(151) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + "1".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
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
