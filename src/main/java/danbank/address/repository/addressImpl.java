package danbank.address.repository;

import danbank.address.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface addressImpl extends JpaRepository<Address,Long> {

    Optional<Address> findByUserIdAndValidToIsNull(Long userId);
    List<Address> findByUserIdOrderByValidDesc(Long userId);

}
