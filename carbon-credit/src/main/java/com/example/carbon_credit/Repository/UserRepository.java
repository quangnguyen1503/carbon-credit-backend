package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findById(String id);

    Optional<User> findByName(String name);  // Sửa từ findByname → findByName
}
