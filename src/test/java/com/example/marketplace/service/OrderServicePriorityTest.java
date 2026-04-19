package com.example.marketplace.service;

import com.example.marketplace.model.*;
import com.example.marketplace.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServicePriorityTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private PromotionService promotionService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock orderRepository.save to return the order passed to it
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testAdminPromotionPriority() {
        // Priority 1: Admin
        Promotion adminPromo = Promotion.builder().id("ADMIN_1").build();
        when(promotionService.getAdminAutoApplyPromotion(any())).thenReturn(adminPromo);
        when(promotionService.applyPromotion(eq("ADMIN_1"), any(), any()))
                .thenReturn(new PromotionService.AppliedPromotion("ADMIN_1", "Admin Sale", new BigDecimal("500")));

        // User selected a different one
        String selectedId = "SELLER_PROMO";

        // Setup product
        Product p = Product.builder().id("p1").price(new BigDecimal("1000")).sellerId("s1").stockQuantity(10).build();
        when(productRepository.findById("p1")).thenReturn(java.util.Optional.of(p));

        orderService.placeSingleOrder("user@test.com", "p1", 1, "Address", "Name", "Phone", selectedId);

        // Verify that ADMIN_1 was recorded, not SELLER_PROMO
        verify(promotionService).incrementPromotionUsageCounter(eq("ADMIN_1"));
        verify(promotionService, never()).incrementPromotionUsageCounter(eq(selectedId));
    }

    @Test
    void testUserSelectedPromotionPriority() {
        // No Admin Promo
        when(promotionService.getAdminAutoApplyPromotion(any())).thenReturn(null);
        
        // User selected SELLER_PROMO
        String selectedId = "SELLER_PROMO";
        when(promotionService.applyPromotion(eq(selectedId), any(), any()))
                .thenReturn(new PromotionService.AppliedPromotion(selectedId, "Seller Sale", new BigDecimal("200")));

        Product p = Product.builder().id("p1").price(new BigDecimal("1000")).sellerId("s1").stockQuantity(10).build();
        when(productRepository.findById("p1")).thenReturn(java.util.Optional.of(p));

        orderService.placeSingleOrder("user@test.com", "p1", 1, "Address", "Name", "Phone", selectedId);

        verify(promotionService).incrementPromotionUsageCounter(eq(selectedId));
    }

    @Test
    void testWelcomePromoFallback() {
        // No Admin, No User Selection
        when(promotionService.getAdminAutoApplyPromotion(any())).thenReturn(null);
        when(promotionService.isFirstPurchase("user@test.com")).thenReturn(true);
        when(promotionService.applyPromotion(eq("WELCOME_PROMO"), any(), any()))
                .thenReturn(new PromotionService.AppliedPromotion("WELCOME_PROMO", "Welcome", new BigDecimal("100")));

        Product p = Product.builder().id("p1").price(new BigDecimal("1000")).sellerId("s1").stockQuantity(10).build();
        when(productRepository.findById("p1")).thenReturn(java.util.Optional.of(p));

        orderService.placeSingleOrder("user@test.com", "p1", 1, "Address", "Name", "Phone", null);

        verify(promotionService).incrementPromotionUsageCounter(eq("WELCOME_PROMO"));
    }
}
