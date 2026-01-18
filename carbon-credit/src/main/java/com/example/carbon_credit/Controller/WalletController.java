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
            // 1. Kiểm tra số dư trong DB (Available balance)
            // 2. Trừ Available, cộng vào Locked (để ngăn dùng số tiền này đi đặt lệnh khác)
            // 3. Gọi contractService.withdrawNative(...)
            // 4. Nếu SC thành công, trừ Locked trong DB.
            return ResponseEntity.ok("Withdrawal processed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-natives")
    public Optional<MyNativeResponse> myNatives (Principal principal){
        return walletService.getMyNatives(principal.getName());
    }


}
