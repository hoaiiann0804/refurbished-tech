package com.example.refurbished.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.phoneNumber = :phone")
    Optional<Customer> findByPhoneNumberForUpdate(@Param("phone") String phone);
    
    boolean existsByPhoneNumber(String phoneNumber);

    @Query("select c from Customer c where lower(c.fullName) like lower(concat('%', :q, '%')) or c.phoneNumber like concat('%', :q, '%')")
    Page<Customer> search(@Param("q") String query, Pageable pageable);
}
