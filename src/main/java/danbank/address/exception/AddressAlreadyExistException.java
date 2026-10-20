package danbank.address.exception;

import danbank.common.exception.ConflictException;

public class AddressAlreadyExistException extends ConflictException {
    public AddressAlreadyExistException() {
        super("This user already has an active address on file");
    }
}
