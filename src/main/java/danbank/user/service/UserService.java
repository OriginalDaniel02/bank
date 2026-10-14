package danbank.user.service;

import danbank.address.services.AddressServices;
import danbank.user.dto.*;
import danbank.user.exception.InvalidCredentialsException;
import danbank.user.exception.UserDuplicateException;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import danbank.util.BvnHash;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static danbank.user.model.User.LastFourDigits;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AddressServices addressServices;

    private final BvnHash encryptBvn;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AddressServices addressServices, BvnHash encryptBvn) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.addressServices = addressServices;
        this.encryptBvn = encryptBvn;
    }

    @Transactional
    public UserResponse signUp(UserRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByPhoneNumber(request.phoneNumber())
                || userRepository.existsByEmail(email)) {
            throw new UserDuplicateException();
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .bvnLast4(LastFourDigits(request.bvn()))
                .bvnHash(encryptBvn.hash(request.bvn()))
                .dateOfBirth(request.dateOfBirth())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved  = userRepository.save(user);
        addressServices.createAddress(saved.getId(), request.address());

        return UserResponse.from(saved);
    }

    public UserResponse loginUser(LoginRequest request) {
        String email =  request.email().strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return UserResponse.from(user);
    }

    public UserResponse getUserById(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user) ;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse getUserByEMail(String email) {
        String mail =  email.strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(mail).orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    @Transactional
    public void closeUser(long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.close();
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if(request.firstName() != null || request.lastName() !=null){
            user.changeNames(
                    request.firstName() != null ? request.firstName() : user.getFirstName(),
                    request.lastName() != null ?  request.lastName() : user.getLastName());
        }

        if(request.email() != null && !request.email().equals(user.getEmail())){
            if(userRepository.existsByEmail(request.email())){
                throw new UserDuplicateException();
            }
            user.changeEmail(request.email());
        }
        if(request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())){
            if(userRepository.existsByPhoneNumber(request.phoneNumber())){
                throw  new UserDuplicateException();
            }
            user.changePhoneNumber(request.phoneNumber());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePassword changePwd){
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if(!passwordEncoder.matches(changePwd.currentPassword(),user.getPasswordHash())){
            throw new InvalidCredentialsException();

        }
        user.changePassword(passwordEncoder.encode(changePwd.changePassword()));
    }






    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.emptyList())
                .build();
    }

}
