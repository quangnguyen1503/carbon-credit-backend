package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.Trade;
import com.example.carbon_credit.Repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistenceService {

    private final TradeRepository tradeRepository;

    @Transactional
    public void saveHistoricalTrade(TradeEventDTO event) {
        Trade trade = Trade.builder()
                .id(event.getTradeId())
                .buyOrderId(event.getBuyOrderId())
                .sellOrderId(event.getSellOrderId())
                .creditId(event.getCreditId())
                .amount(event.getAmount())
                .price(event.getPrice())
                .totalValue(event.getTotalValue())
                .tradeAt(event.getTradeAt())
                .status("PENDING")
                .build();

        tradeRepository.save(trade);
        log.info("💾 Trade {} saved to database", event.getTradeId());
    }
}