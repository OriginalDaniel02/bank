package danbank.address.exception;

import danbank.common.exception.NotFoundException;

public class AddressNotFoundException extends NotFoundException {
    public AddressNotFoundException() {
        super("No address on file for this user");
    }
}
