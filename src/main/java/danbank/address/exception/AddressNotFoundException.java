package danbank.address.exception;

import danbank.common.exception.NotFoundException;

public class AddressNotFoundException extends NotFoundException {
    public AddressNotFoundException() {
        super("Address Not Found");
    }
}
