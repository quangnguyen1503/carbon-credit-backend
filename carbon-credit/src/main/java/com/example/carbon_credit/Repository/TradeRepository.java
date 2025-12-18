package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {
    List<Trade> findByCreditIdAndStatus(String creditId, String status);  // Ví dụ filter trades theo credit
    List<Trade> findByBuyOrderIdOrSellOrderId(String buyOrderId, String sellOrderId);  // Trades của order cụ thể

}