package dan.bank.bank.device;

import dan.bank.bank.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService service;
    private final CurrentUser currentUser;

    public DeviceController(DeviceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Send X-Device-Token to learn which entry is the device you are using. */
    @GetMapping
    public ResponseEntity<List<DeviceService.View>> list(@RequestHeader(value = "X-Device-Token", required = false) String deviceToken) {
        return ResponseEntity.ok(service.list(currentUser.id(), deviceToken));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable long id) {
        service.revoke(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
