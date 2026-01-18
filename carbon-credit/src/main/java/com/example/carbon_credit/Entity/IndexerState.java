package com.example.carbon_credit.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Entity
@Table(name = "indexer_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexerState {
    @Id
    private String id;
    private BigInteger lastScannedBlock;
}
