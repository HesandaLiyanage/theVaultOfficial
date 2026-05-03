package com.hess.thevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TheVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheVaultApplication.class, args);
    }

}
