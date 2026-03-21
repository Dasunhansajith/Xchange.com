package com.example.marketplace.controller;

import com.example.marketplace.model.Order;
import com.example.marketplace.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/place-from-wishlist")
    public ResponseEntity<Order> placeOrderFromWishlist(@RequestBody Map<String, String> payload, Authentication auth) {
        String shippingAddress = payload.get("shippingAddress");
        return ResponseEntity.ok(orderService.placeOrderFromWishlist(auth.getName(), shippingAddress));
    }

    @GetMapping("/me")
    public ResponseEntity<List<Order>> getMyOrders(Authentication auth) {
        return ResponseEntity.ok(orderService.getMyOrders(auth.getName()));
    }
}
