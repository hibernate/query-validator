package test.spring;

import org.springframework.data.jpa.repository.Query;
import test.Address;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AddressRepository extends CrudRepository<Address, Long> {

	Optional<Address> findByZip(String zip);

	@Query("SELECT * FROM Address")
	Optional<Address> badQuery();

	@Query("SELECT a FROM Address a WHERE a.citi = :city")
	Optional<Address> goodQuery();

	@Query(value = "SELECT a FROM Address a WHERE a.zix = :zip", countName = "countNq")
	Optional<Address> goodQueryWithMultipleAnnotationAttribute();
}
