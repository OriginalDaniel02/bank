package danbank.user.dto;

import jakarta.validation.constraints.*;

import java.util.Locale;

public record UserUpdateRequest(
        @Size(min = 2, max = 50, message = "FirstName should not exceed 50 characters" )
        String firstName,
        @Size(min = 2, max = 50, message = "LastName should not exceed 50 characters" )
        String lastName,
        @Email @Size(max = 255, message = "Email must be at most 255 characters")
        String email,
        @Pattern(regexp = "^[0-9]{11}$")
        String phoneNumber

) {
        public UserUpdateRequest {
                email = email == null ? null : clean(email.toLowerCase(Locale.ROOT));
                firstName = clean(firstName);
                lastName = clean(lastName);
                phoneNumber = clean(phoneNumber);
        }
        private static String clean(String s){
                if (s ==null) return null;
                String t =  s.strip();
                return t.isEmpty() ? null : t;
        }




}
