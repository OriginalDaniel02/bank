package danbank.user.service;

import danbank.account.dtos.PageResponse;
import danbank.address.services.AddressService;
import danbank.common.audit.Audit;
import danbank.common.exception.BadRequestException;
import danbank.notification.service.NotificationService;
import danbank.security.AuthUser;
import danbank.security.TotpService;
import danbank.user.dto.ChangePassword;
import danbank.user.dto.TwoFactorSetupResponse;
import danbank.user.dto.UserRequest;
import danbank.user.dto.UserResponse;
import danbank.user.dto.UserUpdateRequest;
import danbank.user.exception.EmailNotFoundException;
import danbank.user.exception.InvalidCredentialsException;
import danbank.user.exception.UserDuplicateException;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import danbank.util.BvnHash;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static danbank.user.model.User.lastFourDigits;
import static danbank.user.model.User.normalizePhoneNumber;

@Service
public class UserService implements UserDetailsService {

    private static final String ISSUER = "DanBank";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AddressService addressServices;
    private final BvnHash encryptBvn;
    private final TotpService totpService;
    private final NotificationService notificationService;

    public UserService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, AddressService addressServices,
            BvnHash encryptBvn, TotpService totpService, NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.addressServices = addressServices;
        this.encryptBvn = encryptBvn;
        this.totpService = totpService;
        this.notificationService = notificationService;
    }

    @Transactional
    public UserResponse signUp(UserRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        String bvnHash = encryptBvn.hash(request.bvn());

        if (userRepository.existsByPhoneNumber(normalizedPhone)
                || userRepository.existsByEmail(email)
                || userRepository.existsByBvnHash(bvnHash)) {
            throw new UserDuplicateException();
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(email)
                .phoneNumber(request.phoneNumber())
                .bvnLast4(lastFourDigits(request.bvn()))
                .bvnHash(bvnHash)
                .dateOfBirth(request.dateOfBirth())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);
        addressServices.createAddress(saved.getId(), request.address());
        Audit.log("USER_SIGNED_UP", "userId={}", saved.getId());
        notificationService.notify(saved.getId(), "WELCOME", "Welcome to " + ISSUER + "! Your account is ready.");

        return UserResponse.from(saved);
    }

    public UserResponse getUserById(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<UserResponse> page = userRepository.findAll(pageable).map(UserResponse::from);
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
        );
    }

    public UserResponse getUserByEmail(String email) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail).orElseThrow(EmailNotFoundException::new);
        return UserResponse.from(user);
    }

    @Transactional
    public void closeUser(long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.close();
        Audit.log("USER_CLOSED", "userId={}", userId);
    }

    @Transactional
    public UserResponse updateUser(long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (request.firstName() != null || request.lastName() != null) {
            user.changeNames(
                    request.firstName() != null ? request.firstName() : user.getFirstName(),
                    request.lastName() != null ? request.lastName() : user.getLastName());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new UserDuplicateException();
            }
            user.changeEmail(request.email());
        }
        if (request.phoneNumber() != null) {
            String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
            if (!normalizedPhone.equals(user.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumber(normalizedPhone)) {
                    throw new UserDuplicateException();
                }
                user.changePhoneNumber(request.phoneNumber());
            }
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(long userId, ChangePassword changePwd) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(changePwd.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.changePassword(passwordEncoder.encode(changePwd.newPassword()));
        notificationService.notify(userId, "SECURITY_ALERT", "Your password was changed.");
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String secret = totpService.generateSecret();
        user.startTwoFactorSetup(secret);
        return new TwoFactorSetupResponse(secret, totpService.otpAuthUri(ISSUER, user.getEmail(), secret));
    }

    @Transactional
    public void enableTwoFactor(long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getTwoFactorSecret() == null || !totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new BadRequestException("Invalid or expired code. Call setup again and try the new code.");
        }
        user.enableTwoFactor();
        Audit.log("TWO_FACTOR_ENABLED", "userId={}", userId);
        notificationService.notify(userId, "SECURITY_ALERT", "Two-factor authentication was turned on for your account.");
    }

    @Transactional
    public void disableTwoFactor(long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getTwoFactorSecret() == null || !totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new BadRequestException("Invalid or expired code");
        }
        user.disableTwoFactor();
        Audit.log("TWO_FACTOR_DISABLED", "userId={}", userId);
        notificationService.notify(userId, "SECURITY_ALERT", "Two-factor authentication was turned off for your account.");
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new AuthUser(user);
    }
}
