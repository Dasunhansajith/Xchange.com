package com.example.marketplace.controller;

import com.example.marketplace.model.Order;
import com.example.marketplace.model.Review;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/test-review/{orderId}")
    public ResponseEntity<Map<String, Object>> testReviewSave(@PathVariable String orderId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            result.put("orderFound", orderOpt.isPresent());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                result.put("itemsNull", order.getItems() == null);
                if (order.getItems() != null) {
                    result.put("itemsSize", order.getItems().size());
                    if (!order.getItems().isEmpty()) {
                        result.put("firstItem", order.getItems().get(0));
                        result.put("firstItemProductId", order.getItems().get(0).getProductId());
                        
                        Review review = reviewRepository.findByOrderId(orderId).orElse(null);
                        result.put("existingReviewFound", review != null);
                        
                        if (review == null) {
                            review = Review.builder()
                                    .orderId(orderId)
                                    .productId(order.getItems().get(0).getProductId())
                                    .productName(order.getProductName())
                                    .buyerId(order.getBuyerId())
                                    .buyerName(order.getBuyerName())
                                    .createdAt(java.time.LocalDateTime.now())
                                    .rating(5)
                                    .comment("Debug review")
                                    .build();
                            result.put("reviewBuilt", true);
                            
                            try {
                                reviewRepository.save(review);
                                result.put("reviewSaved", true);
                            } catch (Exception e) {
                                result.put("reviewSaveError", e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            result.put("globalError", e.getMessage());
            e.printStackTrace();
        }
        return ResponseEntity.ok(result);
    }
}
