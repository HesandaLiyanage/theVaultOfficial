package com.hess.thevault.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class VaultUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public VaultUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = normalizeEmail(username);

        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new UsernameNotFoundException("Invalid username or password");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
