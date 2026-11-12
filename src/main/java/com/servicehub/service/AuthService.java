package com.servicehub.service;

import com.servicehub.dto.auth.LoginRequest;
import com.servicehub.dto.auth.LoginResponse;
import com.servicehub.dto.auth.RegisterRequest;
import com.servicehub.dto.auth.UserSummary;
import com.servicehub.entity.Department;
import com.servicehub.entity.Role;
import com.servicehub.entity.User;
import com.servicehub.exception.ConflictException;
import com.servicehub.exception.ForbiddenOperationException;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.RoleRepository;
import com.servicehub.repository.UserRepository;
import com.servicehub.security.JwtService;
import com.servicehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_REGISTRATION_ROLE = "EMPLOYEE";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.registration.enabled:true}")
    private boolean registrationEnabled;

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

    @Transactional
    public UserSummary register(RegisterRequest request) {
        if (!registrationEnabled) {
            throw new ForbiddenOperationException("Registration is disabled");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department", request.departmentId()));
        }

        // Role is never taken from the client: self-registration always creates an EMPLOYEE.
        Role role = roleRepository.findByName(DEFAULT_REGISTRATION_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + DEFAULT_REGISTRATION_ROLE + " is missing"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(role);
        user.setDepartment(department);
        user.setActive(true);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Email is already registered");
        }

        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getDepartment() == null ? null : user.getDepartment().getId());
    }
}
