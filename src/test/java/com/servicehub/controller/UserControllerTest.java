package com.servicehub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
