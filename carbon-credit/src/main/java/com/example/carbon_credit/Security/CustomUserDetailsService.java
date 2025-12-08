package com.example.carbon_credit.Security;

import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {  // username = user.id
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getId(),
                "",  // No password (signature auth)
                true, true, true, true,  // Enabled, non-expired, etc.
                getAuthorities(user.getRoleId())
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(String roleId) {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + roleId));  // e.g., ROLE_VERIFIER
    }
}