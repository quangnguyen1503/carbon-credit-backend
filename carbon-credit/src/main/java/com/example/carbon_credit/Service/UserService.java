package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.UserDto;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.UserRepository;

public interface UserService {
    User setRole(String id , UserDto req);

    User getUserById(String id);

}
