package dan.bank.bank.user.controller;

import dan.bank.bank.auth.CurrentUser;
import dan.bank.bank.user.dto.UpdateRequest;
import dan.bank.bank.user.dto.UserDto;
import dan.bank.bank.user.dto.UserRequest;
import dan.bank.bank.user.dto.UserResponse;
import dan.bank.bank.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import dan.bank.bank.notification.NotificationService;
import dan.bank.bank.notification.NotificationType;
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
import dan.bank.bank.user.dto.SetPinRequest;
import dan.bank.bank.user.service.PinService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;
    private final NotificationService notifications;
    private final PinService pinService;


    @GetMapping
    public ResponseEntity<UserResponse<List<UserDto>>> getAllUsers() {
        // Users may only see themselves; the full user list is not exposed.
        UserResponse<UserDto> self = userService.getUserById(currentUser.id());
        return ResponseEntity.ok(new UserResponse<>("Users found", List.of(self.data())));
    }

    @GetMapping(params = "email")
    public ResponseEntity<UserResponse<UserDto>> getUserByEmail(
            @RequestParam @Email String email
    ) {
        UserResponse<UserDto> found = userService.getUserByEMail(email);
        currentUser.requireSelf(found.data().id());
        return ResponseEntity.ok(found);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<UserResponse<UserDto>> getUser(@PathVariable Long id) {
        currentUser.requireSelf(id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/signUp")
    public ResponseEntity<UserResponse<UserDto>> signUp(
            @Valid @RequestBody UserRequest user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signUp(user));
    }


    @PostMapping("/me/pin")
    public ResponseEntity<Void> setPin(@Valid @RequestBody SetPinRequest request) {
        pinService.setPin(currentUser.id(), request.pin(), request.password());
        notifications.notifyIndependently(currentUser.id(), NotificationType.SECURITY, "Transaction PIN updated", "Your transaction PIN was set or changed. If this was not you, change your password right away.");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/updateUser/{id}")
    public ResponseEntity<UserResponse<UserDto>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        currentUser.requireSelf(id);
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/deleteUser/{id}")
    public ResponseEntity<UserResponse<UserDto>> deleteUser(@PathVariable long id) {
        currentUser.requireSelf(id);
        return ResponseEntity.ok(userService.deleteUser(id));
    }
}
