package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.VerifierRole;
import com.example.carbon_credit.Repository.VerifierRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VerifierRoleService {
    @Autowired
    private VerifierRoleRepository verifierRoleRepository;
    public List<VerifierRole> getAllVerifierRole(){
        return  verifierRoleRepository.findAll();
    }
}
