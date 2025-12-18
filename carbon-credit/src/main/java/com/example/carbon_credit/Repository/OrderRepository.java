package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByOrderTypeAndStatus(String orderType, String status);  // Giờ OK với 'orderType'
    List<Order> findByUserIdAndStatus(String userId, String status);
}
