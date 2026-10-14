package danbank.address.exception;

import danbank.common.exception.NotFoundException;

public class AddressAlreadyExistException extends NotFoundException {
    public AddressAlreadyExistException() {
        super("Address already Exist");
    }
}
