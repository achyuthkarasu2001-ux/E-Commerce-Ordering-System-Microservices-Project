package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AdminCreateUserRequest;
import com.ecommerce.userservice.dto.UserDto;
import com.ecommerce.userservice.exception.ForbiddenException;
import com.ecommerce.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Base path matches the Gateway route (/api/users/**). By the time a
 * request reaches here, gateway-service's JwtAuthFilter has already
 * validated the JWT and injected the caller's identity as headers:
 *   X-User-Id   — the authenticated caller's user id
 *   X-User-Role — the authenticated caller's role (CUSTOMER or ADMIN)
 *
 * Endpoints that create/delete other users' accounts require ADMIN.
 * Reading your own profile is allowed for any authenticated user (the
 * gateway already guarantees SOME valid token was presented).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody AdminCreateUserRequest dto,
                                           @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(dto));
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    public List<UserDto> getAll(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role); // listing every user's data is an admin-only operation
        return userService.getAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void  requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("This operation requires the ADMIN role");
        }
    }
}
