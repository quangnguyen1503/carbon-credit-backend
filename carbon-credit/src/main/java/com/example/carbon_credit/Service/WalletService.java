package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.Entity.WalletCredit;
import com.example.carbon_credit.Repository.WalletCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletCreditRepository walletCreditRepository;
    public List<MyCreditResponse> getMyCredits(String userId) {

        return walletCreditRepository.findMyCredits(userId);
        }

}
