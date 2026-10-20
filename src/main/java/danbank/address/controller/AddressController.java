package danbank.address.controller;

import danbank.address.dto.AddressRequest;
import danbank.address.dto.AddressResponse;
import danbank.address.services.AddressService;
import danbank.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<AddressResponse>> getCurrentAddress(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Address found", addressService.getCurrentAddress(userId)));
    }

    @GetMapping("/history")
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddressHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Address history found", addressService.getAddressHistory(userId)));
    }

    @PutMapping
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<AddressResponse>> changeAddress(
            @PathVariable Long userId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Address updated", addressService.changeAddress(userId, request)));
    }
}
