package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, String> {

    List<FailedEvent> findByStatus(String status);

    List<FailedEvent> findByStatusOrderByCreatedAtDesc(String status);

    List<FailedEvent> findByEventType(String eventType);

    boolean existsByTransactionHash(String transactionHash);
}
