package com.example.marketplace.service;

import com.example.marketplace.dto.UserDto;
import com.example.marketplace.model.User;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.ShopRepository;
import com.example.marketplace.repository.SellerApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private ShopRepository shopRepository;
    
    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    public UserDto getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    public UserDto updateProfile(String email, UserDto dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        user.setNicNumber(dto.getNicNumber());
        user.setProfilePhotoUrl(dto.getProfilePhotoUrl());

        return mapToDto(userRepository.save(user));
    }

    public UserDto addToWishlist(String email, String productId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getWishlist().add(productId);
        return mapToDto(userRepository.save(user));
    }

    public UserDto removeFromWishlist(String email, String productId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getWishlist().remove(productId);
        return mapToDto(userRepository.save(user));
    }

    public java.util.List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(java.util.stream.Collectors.toList());
    }

    public UserDto updateUserRole(String userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Add the role as ROLE_ADMIN if it doesn't already exist
        String roleFormatted = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
        user.getRoles().add(roleFormatted);
        
        return mapToDto(userRepository.save(user));
    }

    public UserDto removeUserRole(String userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Remove the role
        String roleFormatted = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
        user.getRoles().remove(roleFormatted);
        
        // If removing ROLE_SELLER, ensure the user has ROLE_BUYER so roles are never empty
        if (roleFormatted.equals("ROLE_SELLER")) {
            user.getRoles().add("ROLE_BUYER");
        }
        
        return mapToDto(userRepository.save(user));
    }

    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);
    }

    public void deleteAccountCascade(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String userId = user.getId();
        
        // Delete all products by this seller
        var products = productRepository.findBySellerId(email);
        for (var product : products) {
            productRepository.delete(product);
        }
        
        // Delete all shops associated with this user
        var shops = shopRepository.findAllByUserId(userId);
        for (var shop : shops) {
            shopRepository.delete(shop);
        }
        
        // Delete any seller applications
        var sellerApps = sellerApplicationRepository.findByUserId(userId);
        for (var app : sellerApps) {
            sellerApplicationRepository.delete(app);
        }
        
        // Finally, delete the user
        userRepository.delete(user);
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .createdAt(user.getCreatedAt())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .roles(user.getRoles())
                .wishlist(user.getWishlist())
                .shopId(user.getShopId())
                .hasShop(user.getShopId() != null)
                .build();
    }
}
