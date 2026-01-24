package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.VerifierRole;
import com.example.carbon_credit.Service.VerifierRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/verifier-role")
public class VerifierRoleController {

    @Autowired
    VerifierRoleService verifierRoleService;

    @GetMapping("/all")
    public List<VerifierRole> getAll() {
        return verifierRoleService.getAllVerifierRole();
    }

}
