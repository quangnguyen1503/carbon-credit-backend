package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.DTO.MyNativeResponse;
import com.example.carbon_credit.Service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/my-credits")
    public List<MyCreditResponse> myCredits(
            Principal principal
    ) {
        return walletService.getMyCredits(principal.getName());
    }

    @GetMapping("/my-natives")
    public Optional<MyNativeResponse> myNatives (Principal principal){
        return walletService.getMyNatives(principal.getName());
    }

}
