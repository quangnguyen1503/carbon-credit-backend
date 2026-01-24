package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.UserDTO;
import com.example.carbon_credit.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("api/user")
public class UserController {

    @Autowired
    UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        return ResponseEntity.ok(userService.getUserById(principal.getName()));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUser(@RequestParam String roleId) {
        return ResponseEntity.ok((userService.getAllByRoleid(roleId)));
    }

    @PutMapping("/updateProfile")
    public ResponseEntity<?> updateProfile(@RequestBody UserDTO dto, Principal principal) {
        return ResponseEntity.ok(userService.updateProfile(dto, principal.getName()));

    }

}