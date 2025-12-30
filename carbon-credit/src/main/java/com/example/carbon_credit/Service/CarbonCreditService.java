package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class CarbonCreditService {
    @Autowired
    CarbonCreditRepository carbonCreditRepository;


    public CarbonCredit mintCarbonCredit(CarbonCredit carbonCredit){
        carbonCredit.setIssueAt(LocalDateTime.now());
        return carbonCreditRepository.save(carbonCredit);
    }
}
