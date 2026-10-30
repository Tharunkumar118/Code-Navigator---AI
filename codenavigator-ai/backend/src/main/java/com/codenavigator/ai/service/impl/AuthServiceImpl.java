package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.AuthResponse;
import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.codenavigator.ai.dto.UserProfileResponse;
import com.codenavigator.ai.entity.ActivityLog;
import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.Role;
import com.codenavigator.ai.entity.User;
import com.codenavigator.ai.exception.EmailAlreadyExistsException;
import com.codenavigator.ai.exception.ResourceNotFoundException;
import com.codenavigator.ai.repository.ActivityLogRepository;
import com.codenavigator.ai.repository.RoleRepository;
import com.codenavigator.ai.repository.UserRepository;
import com.codenavigator.ai.security.JwtUtils;
import com.codenavigator.ai.security.UserDetailsImpl;
import com.codenavigator.ai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // NEVER store raw password
        user.setRoles(resolveRoles(request.getRoles()));

        User saved = userRepository.save(user);
        log.info("New user registered: {}", saved.getEmail());
        activityLogRepository.save(new ActivityLog(saved, "USER_REGISTERED", "New account created"));

        // Auto-login after registration: generate a token immediately so the
        // frontend can go straight to the dashboard without a second login step.
        Authentication authentication = authenticateAndGetContext(request.getEmail(), request.getPassword());
        String token = jwtUtils.generateToken(authentication);

        return buildAuthResponse(saved, token);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticateAndGetContext(request.getEmail(), request.getPassword());
        String token = jwtUtils.generateToken(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        log.info("User logged in: {}", user.getEmail());
        activityLogRepository.save(new ActivityLog(user, "USER_LOGIN", "User logged in"));
        return buildAuthResponse(user, token);
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    // --- private helpers ---

    private Authentication authenticateAndGetContext(String email, String rawPassword) {
        // Delegates to DaoAuthenticationProvider -> UserDetailsServiceImpl + PasswordEncoder.
        // Throws BadCredentialsException automatically on mismatch (handled by GlobalExceptionHandler).
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword)
        );
    }

    private Set<Role> resolveRoles(Set<String> requestedRoles) {
        Set<Role> roles = new HashSet<>();

        if (requestedRoles == null || requestedRoles.isEmpty()) {
            roles.add(getRoleOrThrow(ERole.ROLE_STUDENT)); // default role
            return roles;
        }

        for (String r : requestedRoles) {
            ERole eRole = switch (r.trim().toUpperCase()) {
                case "FACULTY" -> ERole.ROLE_FACULTY;
                case "STUDENT" -> ERole.ROLE_STUDENT;
                // ADMIN is intentionally NOT reachable through public self-registration.
                // Admins are provisioned directly in the DB or promoted by an existing Admin (Module 3).
                default -> ERole.ROLE_STUDENT;
            };
            roles.add(getRoleOrThrow(eRole));
        }
        return roles;
    }

    private Role getRoleOrThrow(ERole name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", name));
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());

        return AuthResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(roleNames)
                .accessToken(token)
                .build();
    }
}
