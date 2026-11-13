package dan.bank.bank.beneficiary;

import dan.bank.bank.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;
    private final CurrentUser currentUser;

    public BeneficiaryController(BeneficiaryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record AddBeneficiaryRequest(
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Account number must be 10 digits") String accountNumber,
            @Size(max = 50, message = "Nickname must be 50 characters or fewer") String nickname) { }

    @GetMapping
    public ResponseEntity<List<BeneficiaryService.BeneficiaryDto>> list() {
        return ResponseEntity.ok(service.list(currentUser.id()));
    }

    @PostMapping
    public ResponseEntity<BeneficiaryService.BeneficiaryDto> add(@Valid @RequestBody AddBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.add(currentUser.id(), request.accountNumber(), request.nickname()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
