package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.IndexerState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexerStateRepository extends JpaRepository<IndexerState, String> {

}
