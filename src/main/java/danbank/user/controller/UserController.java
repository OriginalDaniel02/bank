package danbank.user.controller;

import danbank.common.dto.ApiResponse;
import danbank.user.dto.UserUpdateRequest;
import danbank.user.dto.UserRequest;
import danbank.user.dto.UserResponse;
import danbank.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.getAllUsers()));
    }

    @GetMapping(params = "email")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(
            @RequestParam @Email String email
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.getUserByEMail(email)));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping("/signUp")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(
            @Valid @RequestBody UserRequest user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(userService.signUp(user)));
    }


    @PutMapping("/updateUser/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(userId, request)));
    }

    @DeleteMapping("/deleteUser/{id}")
    public void deleteUser(@PathVariable long userId) {
        userService.closeUser(userId);
    }
}
