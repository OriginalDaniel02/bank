package danbank.address.dto;

import danbank.address.model.Address;

public record AddressResponse(
        Long id,
        String state,
        String lga,
        String postalCode,
        String landmark,
        String address1,
        String address2
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getState(),
                address.getLga(),
                address.getPostalCode(),
                address.getLandmark(),
                address.getAddress1(),
                address.getAddress2()
        );
    }
}
