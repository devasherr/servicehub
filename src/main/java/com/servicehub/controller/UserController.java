package com.servicehub.controller;

import com.servicehub.dto.user.UserProfileResponse;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getId()));
    }
}
