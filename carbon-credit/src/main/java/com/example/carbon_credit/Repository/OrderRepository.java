package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    
    List<Order> findByUserIdAndStatus(String userId, String status);


    List<Order> findByOrderTypeAndStatusOrderByPriceDesc(String orderType, String status);

    List<Order> findByOrderTypeAndStatusOrderByPriceAsc(String orderType, String status);

    List<Order> findByCreditIdAndStatus(String creditId, String status);


    List<Order> findByUserIdAndCreditId(String userId, String creditId);

    List<Order> findByUserIdAndStatusAndCreditId(String userId, String status, String creditId);
}
