package danbank.address.dto;


public record addressDto(
        Long id,
        String state,
        String lga,
        String postalCode,
        String landmark,
        String address1,
        String address2

) {
}
