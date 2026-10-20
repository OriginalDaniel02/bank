package danbank.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "State is required")
        @Size(max = 25, message = "State must be at most 25 characters")
        String state,

        @NotBlank(message = "LGA is required")
        @Size(max = 80, message = "LGA must be at most 80 characters")
        String lga,

        @Pattern(regexp = "^[0-9]{6}$", message = "Postal code must be exactly 6 digits")
        String postalCode,

        @NotBlank(message = "Landmark is required")
        @Size(max = 120, message = "Landmark must be at most 120 characters")
        String landmark,

        @NotBlank(message = "Address is required")
        @Size(max = 200 , message = "Address1 should be 200 characters at most")
        String address1,

        @Size(max = 200 , message = "Address2 should be 200 characters at most")
        String address2
) {

    public AddressRequest {
        state  = cleanText(state);
        lga = cleanText(lga);
        postalCode = cleanText(postalCode);
        landmark = cleanText(landmark);
        address1  = cleanText(address1);
        address2 =  cleanText(address2);


    }

    private String cleanText(String s) {
        if (s == null) return null;
        String t = s.strip().toLowerCase();
        return t.isEmpty() ? null : t;
    }
}
