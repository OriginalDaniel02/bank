package danbank;

import danbank.account.dtos.PageResponse;
import danbank.address.dto.AddressRequest;
import danbank.address.services.AddressService;
import danbank.notification.service.NotificationService;
import danbank.security.TotpService;
import danbank.user.dto.ChangePassword;
import danbank.user.dto.UserRequest;
import danbank.user.dto.UserResponse;
import danbank.user.dto.UserUpdateRequest;
import danbank.user.exception.EmailNotFoundException;
import danbank.user.exception.InvalidCredentialsException;
import danbank.user.exception.UserDuplicateException;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import danbank.user.service.UserService;
import danbank.util.BvnHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AddressService addressService;

    @Mock
    private BvnHash bvnHash;

    @Mock
    private TotpService totpService;

    @Mock
    private NotificationService notificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, addressService, bvnHash, totpService, notificationService);
    }

    // ---------- signUp ----------

    @Test
    void signUp_createsUser_whenDetailsAreUnique() {
        UserRequest request = createUserRequest();
        when(bvnHash.hash(request.bvn())).thenReturn("hashed-bvn");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByBvnHash("hashed-bvn")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        UserResponse response = userService.signUp(request);

        assertEquals(request.email(), response.email());
        assertEquals(request.firstName(), response.firstName());
        verify(addressService).createAddress(any(), eq(request.address()));
    }

    @Test
    void signUp_throwsConflict_whenEmailAlreadyExists() {
        UserRequest request = createUserRequest();
        when(bvnHash.hash(request.bvn())).thenReturn("hashed-bvn");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        UserDuplicateException exception = assertThrows(UserDuplicateException.class, () -> userService.signUp(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signUp_throwsConflict_whenPhoneNumberAlreadyExists() {
        UserRequest request = createUserRequest();
        when(bvnHash.hash(request.bvn())).thenReturn("hashed-bvn");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        assertThrows(UserDuplicateException.class, () -> userService.signUp(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signUp_throwsConflict_whenBvnAlreadyExists() {
        UserRequest request = createUserRequest();
        when(bvnHash.hash(request.bvn())).thenReturn("hashed-bvn");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByBvnHash("hashed-bvn")).thenReturn(true);

        assertThrows(UserDuplicateException.class, () -> userService.signUp(request));
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- getUserById ----------

    @Test
    void getUserById_returnsUser_whenFound() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertEquals(user.getEmail(), response.email());
    }

    @Test
    void getUserById_throwsNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.getUserById(1L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // ---------- getAllUsers ----------

    @Test
    void getAllUsers_returnsAllUsersMappedToDto() {
        List<User> allUsers = List.of(createUser(), createUser(), createUser());
        when(userRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(allUsers));

        PageResponse<UserResponse> response = userService.getAllUsers(PageRequest.of(0, 20));

        assertEquals(allUsers.size(), response.content().size());
    }

    // ---------- getUserByEmail ----------

    @Test
    void getUserByEmail_returnsUser_whenFound() {
        User user = createUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserByEmail(user.getEmail());

        assertEquals(user.getEmail(), response.email());
    }

    @Test
    void getUserByEmail_throwsNotFound_whenMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(EmailNotFoundException.class, () -> userService.getUserByEmail("missing@example.com"));
    }

    // ---------- closeUser ----------

    @Test
    void closeUser_closesUser_whenFound() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.closeUser(1L);

        assertEquals(danbank.user.model.UserStatus.CLOSED, user.getStatus());
    }

    @Test
    void closeUser_throwsNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.closeUser(1L));
    }

    // ---------- updateUser ----------

    @Test
    void updateUser_updatesOnlyProvidedFields() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null);

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("Updated", response.firstName());
        assertEquals(user.getLastName(), response.lastName());
    }

    @Test
    void updateUser_throwsConflict_whenNewEmailAlreadyInUse() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        UserUpdateRequest request = new UserUpdateRequest(null, null, "taken@example.com", null);

        assertThrows(UserDuplicateException.class, () -> userService.updateUser(1L, request));
    }

    @Test
    void updateUser_throwsConflict_whenNewPhoneNumberAlreadyInUse() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneNumber("+234 8099999999")).thenReturn(true);
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, "08099999999");

        assertThrows(UserDuplicateException.class, () -> userService.updateUser(1L, request));
    }

    @Test
    void updateUser_throwsNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null);

        assertThrows(UserNotFoundException.class, () -> userService.updateUser(1L, request));
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_changesPassword_whenCurrentPasswordMatches() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pass", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("New@Pass123")).thenReturn("new-encoded-password");
        ChangePassword request = new ChangePassword("current-pass", "New@Pass123");

        userService.changePassword(1L, request);

        assertEquals("new-encoded-password", user.getPasswordHash());
    }

    @Test
    void changePassword_throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", user.getPasswordHash())).thenReturn(false);
        ChangePassword request = new ChangePassword("wrong-pass", "New@Pass123");

        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
                () -> userService.changePassword(1L, request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_returnsUserDetails_whenFound() {
        User user = createUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserDetails userDetails = userService.loadUserByUsername(user.getEmail());

        assertEquals(user.getEmail(), userDetails.getUsername());
        assertEquals(user.getPasswordHash(), userDetails.getPassword());
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFound_whenMissing() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername("missing@example.com"));
    }

    // ---------- helpers ----------

    private UserRequest createUserRequest() {
        String uniqueValue = UUID.randomUUID().toString();
        return new UserRequest(
                "Test",
                "User",
                "0801" + String.format("%07d", Math.abs(uniqueValue.hashCode()) % 10_000_000),
                uniqueValue + "@example.com",
                "12345678901",
                "Str0ng@Pass",
                LocalDate.of(2000, 1, 1),
                new AddressRequest("Lagos", "Ikeja", "100001", "Near the big mall", "12 Allen Avenue", null)
        );
    }

    private User createUser() {
        String uniqueValue = UUID.randomUUID().toString();
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(uniqueValue + "@example.com")
                .phoneNumber("0801" + String.format("%07d", Math.abs(uniqueValue.hashCode()) % 10_000_000))
                .bvnHash(uniqueValue)
                .bvnLast4("1234")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .passwordHash("encoded-password")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
