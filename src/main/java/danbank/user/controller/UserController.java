package danbank.user.controller;

import danbank.account.dtos.PageResponse;
import danbank.common.dto.ApiResponse;
import danbank.user.dto.ChangePassword;
import danbank.user.dto.UserRequest;
import danbank.user.dto.UserResponse;
import danbank.user.dto.UserUpdateRequest;
import danbank.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(ApiResponse.ok("Users found", userService.getAllUsers(pageable)));
    }

    @GetMapping(params = "email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(
            @RequestParam @Email String email
    ) {
        return ResponseEntity.ok(ApiResponse.ok("User found", userService.getUserByEmail(email)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable long userId) {
        return ResponseEntity.ok(ApiResponse.ok("User found", userService.getUserById(userId)));
    }

    @PostMapping("/signUp")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(
            @Valid @RequestBody UserRequest user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.signUp(user)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("User updated", userService.updateUser(userId, request)));
    }

    @PatchMapping("/{userId}/password")
    @PreAuthorize("@accessPolicy.isSelf(authentication, #userId)")
    public ResponseEntity<Void> changePassword(
            @PathVariable long userId,
            @Valid @RequestBody ChangePassword request
    ) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<Void> closeUser(@PathVariable long userId) {
        userService.closeUser(userId);
        return ResponseEntity.noContent().build();
    }
}
