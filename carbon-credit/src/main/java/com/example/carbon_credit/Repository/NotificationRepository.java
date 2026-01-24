package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository
                extends JpaRepository<Notification, String> {

        @Query("SELECT n FROM Notification n WHERE " +
                        "(LOWER(n.recipient) = LOWER(:wallet)) OR " +
                        "(n.targetRole = :role) OR " +
                        "(n.recipient IS NULL AND n.targetRole IS NULL) " +
                        "ORDER BY n.createdAt DESC")
        List<Notification> findMyNotifications(@Param("wallet") String wallet, @Param("role") String role);
}
