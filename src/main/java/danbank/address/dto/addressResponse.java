package danbank.address.dto;

import danbank.address.model.Address;

public record addressResponse(
        Long id,
        String state,
        String lga,
        String postalCode,
        String landmark,
        String address1,
        String address2
) {
    public static addressResponse from(Address a) {
        return new addressResponse(
                a.getId(), a.getState(), a.getLga(), a.getPostalCode(),
                a.getLandmark(), a.getAddress1(), a.getAddress2());
    }
}
