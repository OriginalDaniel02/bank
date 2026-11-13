package dan.bank.bank.User.controller;

import dan.bank.bank.Auth.CurrentUser;
import dan.bank.bank.User.dto.UpdateRequest;
import dan.bank.bank.User.dto.UserDto;
import dan.bank.bank.User.dto.UserRequest;
import dan.bank.bank.User.dto.UserResponse;
import dan.bank.bank.User.service.UserService;
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
    private final CurrentUser currentUser;
    private final dan.bank.bank.User.service.PinService pinService;


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
    public ResponseEntity<Void> setPin(@Valid @RequestBody dan.bank.bank.User.dto.SetPinRequest request) {
        pinService.setPin(currentUser.id(), request.pin(), request.password());
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
