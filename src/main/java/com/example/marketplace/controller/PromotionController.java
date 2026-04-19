package com.example.marketplace.controller;

import com.example.marketplace.dto.PromotionDto;
import com.example.marketplace.model.PromotionCreator;
import com.example.marketplace.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    @PostMapping
    public ResponseEntity<PromotionDto> createPromotion(@RequestBody PromotionDto dto, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        // Ensure the sellerId is always set to the authenticated user, regardless of what the client sends
        if (dto.getCreatedBy() == null || dto.getCreatedBy() == PromotionCreator.SELLER) {
            dto.setSellerId(auth.getName());
            dto.setCreatedBy(PromotionCreator.SELLER);
        }
        return ResponseEntity.ok(promotionService.createPromotion(dto));
    }

    /**
     * GET /api/promotions/seller
     * Returns ONLY the logged-in seller's own promotions.
     * Sellers must NOT see admin or other sellers' promotions here.
     */
    @GetMapping("/seller")
    public ResponseEntity<List<PromotionDto>> getMySellerPromotions(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(promotionService.getSellerPromotions(auth.getName()));
    }

    @GetMapping("/admin")
    public ResponseEntity<List<PromotionDto>> getAdminPromotions() {
        return ResponseEntity.ok(promotionService.getAdminPromotions());
    }

    @GetMapping("/available")
    public ResponseEntity<List<PromotionDto>> getAvailablePromotions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String cartItems) {
        
        List<String> sellerIds = new ArrayList<>();
        if (cartItems != null && !cartItems.isEmpty()) {
            sellerIds = Arrays.asList(cartItems.split(","));
        }
        
        return ResponseEntity.ok(promotionService.getAvailablePromotions(userId, sellerIds));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionDto> getPromotion(@PathVariable String id) {
        try {
            return ResponseEntity.ok(promotionService.getPromotion(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromotion(@PathVariable String id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }
}
