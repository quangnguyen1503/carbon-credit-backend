package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.OhlcCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OhlcCandleRepository extends JpaRepository<OhlcCandle, String> {

    List<OhlcCandle> findByCreditIdAndTimeframeAndTimestampGreaterThanOrderByTimestampAsc(
            String credit_id,
            String timeframe,
            Long since
    );

    Optional<OhlcCandle> findByCreditIdAndTimeframeAndTimestamp(
            String credit_id,
            String timeframe,
            Long timestamp
    );
}