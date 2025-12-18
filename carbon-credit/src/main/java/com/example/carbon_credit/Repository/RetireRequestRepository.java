package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.RetireRequest;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface RetireRequestRepository extends JpaRepository<RetireRequest, String> {

    Optional<RetireRequest> findById(String id);

}
