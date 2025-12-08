package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final OrderRepository repo;
    private final ContractService contractService;
    private final WsService wsService;

    public void processMatching() throws Exception {

        // Sửa: Dùng orderType thay side (theo entity Order có field orderType)
        List<Order> buyOrders = repo.findByOrderTypeAndStatus("BUY", "OPEN");
        List<Order> sellOrders = repo.findByOrderTypeAndStatus("SELL", "OPEN");

        for (Order buy : buyOrders) {
            for (Order sell : sellOrders) {

                // Điều kiện match (sửa compareTo cho BigDecimal price)
                if (buy.getPrice().compareTo(sell.getPrice()) >= 0 &&
                        buy.getCreditId().equals(sell.getCreditId())) {

                    // Sửa: Cast matchedAmount to int (amount là Integer)
                    int matchedAmount = Math.min(buy.getAmount(), sell.getAmount());

                    // Gọi settleTrade on-chain (4 params: buyer, seller, amount, price)
                    String tx = contractService.settleTrade(
                            buy.getUserId(),
                            sell.getUserId(),
                            (long) matchedAmount,  // int to Long nếu contract cần
                            buy.getPrice().longValue()  // BigDecimal to long cho price
                    );

                    // Sửa: Subtract matchedAmount (int) từ amount (Integer)
                    buy.setAmount(buy.getAmount() - matchedAmount);
                    sell.setAmount(sell.getAmount() - matchedAmount);

                    if (buy.getAmount() == 0) buy.setStatus("MATCHED");
                    if (sell.getAmount() == 0) sell.setStatus("MATCHED");

                    repo.save(buy);
                    repo.save(sell);

                    wsService.broadcastTrade(buy, sell, matchedAmount, tx);
                }
            }
        }
    }
}