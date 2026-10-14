package danbank.address.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAddress(

        @Size(max = 50, message = "State must be at most 50 characters ")
        String state,


        @Size(max = 80, message = "LGA must be at most 80 characters")
        String lga,

        @Pattern(regexp = "^[0-9]{6}$", message = "Postal code must be exactly 6 digits")
        String postalCode,


        @Size(max = 120, message = "Landmark should not be more thn 120 characters")
        String landmark,


        @Size(max = 200 , message = "Address1 should be 200 characters at most")
        String address1,

        @Size(max = 200 , message = "Address2 should be 200 characters at most")
        String address2
) {
}
