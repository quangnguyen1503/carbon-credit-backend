package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Certificate;
import com.example.carbon_credit.Entity.CertificateRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CertificateRecordRepository extends JpaRepository<CertificateRecord, String> {


}
