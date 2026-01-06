package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.UserDTO;
import com.example.carbon_credit.Entity.User;

public interface UserService {
    User setRole(String id , UserDTO req);
    User getUserById(String id);
    User updateProfile(UserDTO dto, String id);

}
