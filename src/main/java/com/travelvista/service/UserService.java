package com.travelvista.service;

import com.travelvista.config.JwtUtil;
import com.travelvista.dto.LoginRequest;
import com.travelvista.dto.LoginResponse;
import com.travelvista.model.Role;
import com.travelvista.model.User;
import com.travelvista.repository.RoleRepository;
import com.travelvista.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !user.getIsActive()) {
            return null;
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return null;
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "user";
        String token = jwtUtil.generateToken(user.getEmail(), roleName, user.getName());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roleName
        );

        return new LoginResponse(token, userInfo);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public LoginResponse registerCustomer(String name, String email, String phone, String password) {
        // Find or create customer role
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role("customer", "Regular website user")));

        User user = new User(name, email, phone, passwordEncoder.encode(password), customerRole);
        user.setIsActive(true);
        User saved = userRepository.save(user);

        String token = jwtUtil.generateToken(saved.getEmail(), "customer", saved.getName());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                saved.getId(), saved.getName(), saved.getEmail(), "customer"
        );
        return new LoginResponse(token, userInfo);
    }
}
