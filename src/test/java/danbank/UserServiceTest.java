package danbank;

import danbank.user.dto.LoginRequest;
import danbank.user.dto.UserUpdateRequest;
import danbank.user.dto.UserRequest;
import danbank.user.dto.UserResponse;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import danbank.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, passwordEncoder);
    }

    // ---------- signUp ----------

    @Test
    void signUp_createsUser_whenEmailAndPhoneAreUnique() {
        UserRequest request = createUserRequest();
        when(userRepo.existsByEmail(request.email())).thenReturn(false);
        when(userRepo.existsByPhoneNumber(request.phoneNumber())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse<UserRequest> response = userService.signUp(request);

        assertEquals("User created", response.message());
        assertEquals(request.email(), response.data().email());
        assertEquals("encoded-password", captureSavedUser().getPassword());
    }

    @Test
    void signUp_throwsConflict_whenEmailAlreadyExists() {
        UserRequest request = createUserRequest();
        when(userRepo.existsByPhoneNumber(request.phoneNumber())).thenReturn(false);
        when(userRepo.existsByEmail(request.email())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.signUp(request));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void signUp_throwsConflict_whenPhoneNumberAlreadyExists() {
        UserRequest request = createUserRequest();
        when(userRepo.existsByPhoneNumber(request.phoneNumber())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.signUp(request));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepo, never()).save(any(User.class));
    }

    // ---------- loginUser ----------

    @Test
    void loginUser_returnsUser_whenCredentialsAreValid() {
        User user = createUser();
        LoginRequest request = new LoginRequest(user.getEmail(), "raw-password");
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw-password", user.getPassword())).thenReturn(true);

        UserResponse<UserRequest> response = userService.loginUser(request);

        assertEquals("Login verified", response.message());
        assertEquals(user.getEmail(), response.data().email());
    }

    @Test
    void loginUser_throwsUnauthorized_whenUserNotFound() {
        LoginRequest request = new LoginRequest("missing@example.com", "raw-password");
        when(userRepo.findByEmail(request.email())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.loginUser(request));

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginUser_throwsUnauthorized_whenPasswordDoesNotMatch() {
        User user = createUser();
        LoginRequest request = new LoginRequest(user.getEmail(), "wrong-password");
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.loginUser(request));

        assertEquals(401, ex.getStatusCode().value());
    }

    // ---------- getUserById ----------

    @Test
    void getUserById_returnsUser_whenFound() {
        User user = createUser();
        user.setId(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        UserResponse<UserRequest> response = userService.getUserById(1L);

        assertEquals("User found", response.message());
        assertEquals(1L, response.data().id());
    }

    @Test
    void getUserById_throwsNotFound_whenMissing() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.getUserById(1L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // ---------- getAllUsers ----------

    @Test
    void getAllUsers_returnsAllUsersMappedToDto() {
        List<User> allUsers = List.of(createUser(), createUser(), createUser());
        when(userRepo.findAll()).thenReturn(allUsers);

        UserResponse<List<UserRequest>> response = userService.getAllUsers();

        assertEquals("Users found", response.message());
        assertEquals(allUsers.size(), response.data().size());
    }

    // ---------- getUserByEMail ----------

    @Test
    void getUserByEmail_returnsUser_whenFound() {
        User user = createUser();
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserResponse<UserRequest> response = userService.getUserByEMail(user.getEmail());

        assertEquals("User found", response.message());
        assertEquals(user.getEmail(), response.data().email());
    }

    @Test
    void getUserByEmail_throwsNotFound_whenMissing() {
        when(userRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.getUserByEMail("missing@example.com"));

        assertEquals(404, ex.getStatusCode().value());
    }

    // ---------- deleteUser ----------

    @Test
    void deleteUser_deletesUser_whenFound() {
        User user = createUser();
        user.setId(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        UserResponse<UserRequest> response = userService.deleteUser(1L);

        assertEquals("User deleted", response.message());
        assertNull(response.data());
        verify(userRepo, times(1)).delete(user);
    }

    @Test
    void deleteUser_throwsNotFound_whenMissing() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.deleteUser(1L));

        assertEquals(404, ex.getStatusCode().value());
        verify(userRepo, never()).delete(any(User.class));
    }

    // ---------- updateUser ----------

    @Test
    void updateUser_updatesOnlyProvidedFields() {
        User user = createUser();
        user.setId(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null, null, null, null);

        UserResponse<UserRequest> response = userService.updateUser(1L, request);

        assertEquals("User updated", response.message());
        assertEquals("Updated", response.data().firstName());
        assertEquals(user.getLastName(), response.data().lastName());
    }

    @Test
    void updateUser_throwsConflict_whenNewEmailAlreadyInUse() {
        User user = createUser();
        user.setId(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.existsByEmail("taken@example.com")).thenReturn(true);
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, "taken@example.com", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(1L, request));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void updateUser_throwsConflict_whenNewPhoneNumberAlreadyInUse() {
        User user = createUser();
        user.setId(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.existsByPhoneNumber("08099999999")).thenReturn(true);
        UserUpdateRequest request = new UserUpdateRequest(null, null, "08099999999", null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(1L, request));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void updateUser_throwsNotFound_whenMissing() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());
        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(1L, request));

        assertEquals(404, ex.getStatusCode().value());
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_returnsUserDetails_whenFound() {
        User user = createUser();
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserDetails userDetails = userService.loadUserByUsername(user.getEmail());

        assertEquals(user.getEmail(), userDetails.getUsername());
        assertEquals(user.getPassword(), userDetails.getPassword());
        assertFalse(userDetails.getAuthorities().iterator().hasNext());
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFound_whenMissing() {
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername("missing@example.com"));
    }

    // ---------- helpers ----------

    private User captureSavedUser() {
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        return captor.getValue();
    }

    private UserRequest createUserRequest() {
        String uniqueValue = UUID.randomUUID().toString();
        return new UserRequest(
                "Test",
                "User",
                "080" + Math.abs(uniqueValue.hashCode()),
                "Test address",
                uniqueValue + "@example.com",
                "Str0ng@Pass",
                LocalDate.of(2000, 1, 1)
        );
    }

    private User createUser() {
        String uniqueValue = UUID.randomUUID().toString();
        return User.builder()
                .firstName("Test")
                .lastName("User")
                .email(uniqueValue + "@example.com")
                .phoneNumber("080" + Math.abs(uniqueValue.hashCode()))
                .address("Test address")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .password("encoded-password")
                .build();
    }
}
