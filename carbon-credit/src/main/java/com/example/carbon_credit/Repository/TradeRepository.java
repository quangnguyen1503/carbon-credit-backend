package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Trade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    List<Trade> findByCreditIdAndStatus(String creditId, String status);

    List<Trade> findByBuyOrderIdOrSellOrderId(String buyOrderId, String sellOrderId);

    List<Trade> findByCreditIdOrderByTradeAtDesc(String credit_id, Pageable pageable);

    // For trade recovery
    List<Trade> findByStatus(String status);

    List<Trade> findByStatusAndTradeAtBefore(String status, LocalDateTime threshold);
}
