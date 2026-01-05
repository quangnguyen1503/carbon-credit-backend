package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Entity.RetireRequest;
import com.example.carbon_credit.Service.CarbonCreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/carboncredits")
public class CarbonCreditController {

    @Autowired
    CarbonCreditService carbonCreditService;

    @PostMapping("/mint")
    public CarbonCredit mintCarbonCredit(@RequestBody CarbonCredit carbonCredit){
        return carbonCreditService.mintCarbonCredit(carbonCredit);
    }




}
