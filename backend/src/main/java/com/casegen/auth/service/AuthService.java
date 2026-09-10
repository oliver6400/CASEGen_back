package com.casegen.auth.service;

import com.casegen.auth.domain.RefreshToken;
import com.casegen.auth.domain.SystemBootstrap;
import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.auth.dto.AuthTokenResponse;
import com.casegen.auth.dto.BootstrapStatusResponse;
import com.casegen.auth.dto.CurrentUserResponse;
import com.casegen.auth.dto.LoginRequest;
import com.casegen.auth.dto.RefreshTokenRequest;
import com.casegen.auth.dto.RegisterRequest;
import com.casegen.auth.exception.AuthErrorCode;
import com.casegen.auth.exception.AuthException;
import com.casegen.auth.repository.RefreshTokenRepository;
import com.casegen.auth.repository.SystemBootstrapRepository;
import com.casegen.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemBootstrapRepository bootstrapRepository;
    private final TokenService tokenService;
    private final CurrentUserService currentUserService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            SystemBootstrapRepository bootstrapRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.bootstrapRepository = bootstrapRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        SystemBootstrap bootstrap = bootstrapRepository
                .findByIdForUpdate(SystemBootstrap.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("System bootstrap row is missing"));
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw conflictEmail();
        }
        try {
            SystemRole role = bootstrap.isSuperuserCreated()
                    ? SystemRole.USER
                    : SystemRole.SUPERUSER;
            User user = userRepository.saveAndFlush(new User(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.displayName().trim(),
                    role));
            if (role == SystemRole.SUPERUSER) {
                bootstrap.completeWith(user, Instant.now());
                bootstrapRepository.saveAndFlush(bootstrap);
            }
            return tokenService.issueTokenPair(user);
        } catch (DataIntegrityViolationException exception) {
            throw conflictEmail();
        }
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(User::isEnabled)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return tokenService.issueTokenPair(user);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = findRefreshToken(request.refreshToken());
        Instant now = Instant.now();
        if (!refreshToken.isUsableAt(now)) {
            throw invalidRefreshToken();
        }
        refreshToken.revoke(now);
        return tokenService.issueTokenPair(refreshToken.getUser());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(request.refreshToken()))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me() {
        User user = currentUserService.requireCurrentUser();
        return new CurrentUserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getSystemRole());
    }

    @Transactional(readOnly = true)
    public BootstrapStatusResponse bootstrapStatus() {
        boolean completed = bootstrapRepository.findById(SystemBootstrap.SINGLETON_ID)
                .map(SystemBootstrap::isSuperuserCreated)
                .orElseThrow(() -> new IllegalStateException("System bootstrap row is missing"));
        return new BootstrapStatusResponse(completed);
    }

    private RefreshToken findRefreshToken(String value) {
        return refreshTokenRepository.findByTokenHash(tokenService.hash(value))
                .orElseThrow(this::invalidRefreshToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthException conflictEmail() {
        return new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT,
                "Email is already registered");
    }

    private AuthException invalidCredentials() {
        return new AuthException(AuthErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                "Email or password is invalid");
    }

    private AuthException invalidRefreshToken() {
        return new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired, or revoked");
    }
}
