package danbank.address.services;

import danbank.address.dto.AddressRequest;
import danbank.address.dto.addressResponse;
import danbank.address.exception.AddressNotFoundException;
import danbank.address.model.Address;
import danbank.address.repository.addressImpl;
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
public class AddressServices {


    private final addressImpl addressRepository;
    private final UserRepository userRepository;


    @Transactional(readOnly = true)
    public addressResponse getCurrentAddress(Long userId){
        Address validateAddress = addressRepository.findByUserIdAndValidToIsNull(userId)
                .orElseThrow(AddressNotFoundException::new);

        return addressResponse.from(validateAddress);
    }

    @Transactional(readOnly = true)
    public List<addressResponse> getAddressHistory(Long userId){
        return addressRepository
                .findByUserIdOrderByValidDesc(userId).stream()
                .map(addressResponse::from)
                .toList();
    }

    @Transactional
    public addressResponse createAddress(Long userId, AddressRequest address){
        if(addressRepository.findByUserIdAndValidToIsNull(userId).isPresent()){
            throw new AddressNotFoundException();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return saveNew(user,address);
    }

    @Transactional
    public addressResponse changeAddress(Long userId, AddressRequest req){
        Address add =  addressRepository.findByUserIdAndValidToIsNull(userId)
                .orElseThrow(AddressNotFoundException::new);
        add.close(Instant.now());
        addressRepository.flush();
        return saveNew(add.getUser(), req);

    }



    private addressResponse saveNew(User user, AddressRequest req){
        Address userAddress =  Address.builder()
                .user(user)
                .landmark(req.landmark())
                .address1(req.address1())
                .address2(req.address2())
                .state(req.state())
                .lga(req.lga())
                .postalCode(req.postalCode())
                .build();
        return addressResponse.from(addressRepository.save(userAddress));
    }







}
