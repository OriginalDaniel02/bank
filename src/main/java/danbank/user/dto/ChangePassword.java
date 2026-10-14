package danbank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePassword(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 2, max = 20) String changePassword
) {
    @Override
    public String toString(){
        return "ChangingPassword[hidden]";
    }

}
