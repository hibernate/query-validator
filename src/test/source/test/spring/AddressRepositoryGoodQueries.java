package test.spring;

import org.springframework.data.jpa.repository.Query;
import test.Address;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AddressRepositoryGoodQueries extends CrudRepository<Address, Long> {

	Optional<Address> findByZip(String zip);

	@Query("SELECT a FROM Address a WHERE a.city = :city")
	Optional<Address> goodQuery();

	@Query("SELECT a FROM Address a WHERE a.city = :city")
	Optional<Address> anotherGoodQuery(String city);

	@Query(value = "SELECT a FROM Address a WHERE a.citi = :city", nativeQuery = true)
	Optional<Address> goodQueryNative();
}
