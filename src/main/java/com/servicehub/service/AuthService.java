package com.servicehub.service;

import com.servicehub.dto.auth.LoginRequest;
import com.servicehub.dto.auth.LoginResponse;
import com.servicehub.dto.auth.UserSummary;
import com.servicehub.security.JwtService;
import com.servicehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);

        UserSummary user = new UserSummary(
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole(),
                principal.getDepartmentId());

        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), user);
    }
}
