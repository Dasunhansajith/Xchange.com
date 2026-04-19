package com.example.marketplace.controller;

import com.example.marketplace.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    @Autowired
    private PromotionService promotionService;

    // @review: Handles the promotion application preview during checkout.
    // Maps to /api/checkout/apply-promotion
    @PostMapping("/apply-promotion")
    public ResponseEntity<PromotionService.AppliedPromotion> applyPromotion(@RequestBody Map<String, Object> payload) {
        String promotionId = (String) payload.get("promotionId");
        String userId = (String) payload.get("userId");
        BigDecimal subtotal = new BigDecimal(payload.get("subtotal").toString());

        return ResponseEntity.ok(promotionService.applyPromotion(promotionId, subtotal, userId));
    }
}
