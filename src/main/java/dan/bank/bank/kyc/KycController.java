package dan.bank.bank.kyc;

import dan.bank.bank.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycService kycService;
    private final CurrentUser currentUser;

    public KycController(KycService kycService, CurrentUser currentUser) {
        this.kycService = kycService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/face", consumes = "multipart/form-data")
    public ResponseEntity<KycService.KycStatusView> verifyFace(@RequestParam("selfie") MultipartFile selfie)
            throws IOException {
        return ResponseEntity.ok(kycService.verifyFace(currentUser.id(), selfie.getBytes()));
    }

    @GetMapping("/status")
    public ResponseEntity<KycService.KycStatusView> status() {
        return ResponseEntity.ok(kycService.status(currentUser.id()));
    }
}
