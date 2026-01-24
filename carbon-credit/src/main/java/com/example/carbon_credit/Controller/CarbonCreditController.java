package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.MintRequestDTO;
import com.example.carbon_credit.Service.CarbonCreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api")
public class CarbonCreditController {

        @Autowired
        CarbonCreditService carbonCreditService;

        @PostMapping("/projects/{projectId}/mint")
        public ResponseEntity<?> mint(
                        @PathVariable String projectId,
                        @RequestBody MintRequestDTO request,
                        Principal principal) {
                return ResponseEntity.ok(
                                carbonCreditService.mintCarbonCredit(
                                                projectId,
                                                principal.getName(),
                                                request.getTxHash(),
                                                request.getMintAmount()));
        }

}
