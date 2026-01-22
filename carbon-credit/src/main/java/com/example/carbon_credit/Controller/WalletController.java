package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.DTO.MyNativeResponse;
import com.example.carbon_credit.Service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @PostMapping("/withdraw/native")
    public ResponseEntity<?> requestWithdraw(@RequestParam String address, @RequestParam BigInteger amount) {
        try {
            return ResponseEntity.ok("Withdrawal processed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-credits")
    public List<MyCreditResponse> myCredits (Principal principal){
        return walletService.getMyCredits(principal.getName());
    }

    @GetMapping("/my-natives")
    public Optional<MyNativeResponse> myNatives (Principal principal){
        return walletService.getMyNatives(principal.getName());
    }

}
