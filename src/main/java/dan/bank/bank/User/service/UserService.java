package dan.bank.bank.User.service;

import dan.bank.bank.User.dto.LoginRequest;
import dan.bank.bank.User.dto.UpdateRequest;
import dan.bank.bank.User.dto.UserDto;
import dan.bank.bank.User.dto.UserRequest;
import dan.bank.bank.User.dto.UserResponse;
import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse<UserDto> signUp(UserRequest request) {
        if (userRepository.existsByPhoneNumber(request.phoneNumber())
                || userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or phone number is already in use");
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .address(request.address())
                .dateOfBirth(request.dob())
                .password(passwordEncoder.encode(request.password()))
                .build();

        return new UserResponse<>("User created", toDto(userRepository.save(user)));
    }

    public UserResponse<UserDto> loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }
        return new UserResponse<>("Login verified", toDto(user));
    }

    public UserResponse<UserDto> getUserById(long userId) {
        User user = userRepository.findById(userId).orElseThrow(this::userNotFound);
        return new UserResponse<>("User found", toDto(user));
    }

    public UserResponse<List<UserDto>> getAllUsers() {
        List<UserDto> users = userRepository.findAll().stream().map(this::toDto).toList();
        return new UserResponse<>("Users found", users);
    }

    public UserResponse<UserDto> getUserByEMail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(this::userNotFound);
        return new UserResponse<>("User found", toDto(user));
    }

    @Transactional
    public UserResponse<UserDto> deleteUser(long userId) {
        User user = userRepository.findById(userId).orElseThrow(this::userNotFound);
        userRepository.delete(user);
        return new UserResponse<>("User deleted", null);
    }

    @Transactional
    public UserResponse<UserDto> updateUser(Long userId, UpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(this::userNotFound);

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.address() != null) user.setAddress(request.address());
        if (request.dob() != null) user.setDateOfBirth(request.dob());
        if (request.password() != null) user.setPassword(passwordEncoder.encode(request.password()));
        updatePhoneNumber(user, request.phoneNumber());
        updateEmail(user, request.email());

        return new UserResponse<>("User updated", toDto(user));
    }

    private void updatePhoneNumber(User user, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.equals(user.getPhoneNumber())) return;
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already in use");
        }
        user.setPhoneNumber(phoneNumber);
    }

    private void updateEmail(User user, String email) {
        if (email == null || email.equals(user.getEmail())) return;
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }
        user.setEmail(email);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(Collections.emptyList())
                .build();
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhoneNumber(), user.getAddress(), user.getDateOfBirth());
    }

    private ResponseStatusException userNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
