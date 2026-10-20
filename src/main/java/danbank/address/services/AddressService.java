package danbank.address.services;

import danbank.address.dto.AddressRequest;
import danbank.address.dto.AddressResponse;
import danbank.address.exception.AddressAlreadyExistException;
import danbank.address.exception.AddressNotFoundException;
import danbank.address.model.Address;
import danbank.address.repository.AddressRepository;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AddressResponse getCurrentAddress(Long userId) {
        Address currentAddress = addressRepository.findByUserIdAndValidToIsNull(userId)
                .orElseThrow(AddressNotFoundException::new);

        return AddressResponse.from(currentAddress);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddressHistory(Long userId) {
        return addressRepository
                .findByUserIdOrderByValidFromDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest address) {
        if (addressRepository.findByUserIdAndValidToIsNull(userId).isPresent()) {
            throw new AddressAlreadyExistException();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return saveNew(user, address);
    }

    @Transactional
    public AddressResponse changeAddress(Long userId, AddressRequest req) {
        Address currentAddress = addressRepository.findByUserIdAndValidToIsNull(userId)
                .orElseThrow(AddressNotFoundException::new);
        currentAddress.close(Instant.now());
        addressRepository.flush();
        return saveNew(currentAddress.getUser(), req);
    }

    private AddressResponse saveNew(User user, AddressRequest req) {
        Address userAddress = Address.builder()
                .user(user)
                .landmark(req.landmark())
                .address1(req.address1())
                .address2(req.address2())
                .state(req.state())
                .lga(req.lga())
                .postalCode(req.postalCode())
                .build();
        return AddressResponse.from(addressRepository.save(userAddress));
    }
}
