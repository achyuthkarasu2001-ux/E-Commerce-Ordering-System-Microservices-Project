package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AdminCreateUserRequest;
import com.ecommerce.userservice.dto.UserDto;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Admin-only path (see UserController) — self-registration goes through AuthService instead. */
    public UserDto create(AdminCreateUserRequest dto) {
        if (userRepository.findByUsername(dto.username()).isPresent()) {
            throw new UserAlreadyExistsException("username", dto.username());
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new UserAlreadyExistsException("email", dto.email());
        }
        User user = User.builder()
                .username(dto.username())
                .email(dto.email())
                .fullName(dto.fullName())
                .password(passwordEncoder.encode(dto.password()))
                .role(dto.role() != null ? dto.role() : "CUSTOMER")
                .build();
        return toDto(userRepository.save(user));
    }

    public UserDto getById(Long id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<UserDto> getAll() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getRole());
    }
}
