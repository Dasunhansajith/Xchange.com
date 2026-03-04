package com.example.marketplace.controller;

import com.example.marketplace.dto.UserDto;
import com.example.marketplace.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateMe(@Valid @RequestBody UserDto dto, Authentication auth) {
        return ResponseEntity.ok(userService.updateProfile(auth.getName(), dto));
    }
}
