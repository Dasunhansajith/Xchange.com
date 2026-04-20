package com.example.marketplace.service;

import com.example.marketplace.model.*;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PromotionServiceTest {

    @InjectMocks
    private PromotionService promotionService;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private OrderRepository orderRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFirstPurchaseDetection() {
        when(orderRepository.countByBuyerId("user1")).thenReturn(0L);
        assertTrue(promotionService.isFirstPurchase("user1"));

        when(orderRepository.countByBuyerId("user1")).thenReturn(1L);
        assertFalse(promotionService.isFirstPurchase("user1"));
    }

    @Test
    void testOneTimeUsageRestriction() {
        String promoId = "ONE_TIME_PROMO";
        Promotion p = Promotion.builder()
                .id(promoId)
                .usageType(UsageType.ONE_TIME)
                .startDate(NOW.minusDays(1))
                .endDate(NOW.plusDays(1))
                .discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10"))
                .currentUsage(0)
                .build();

        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(p));
        when(orderRepository.existsByBuyerIdAndAppliedPromotionId("user1", promoId)).thenReturn(true);

        var result = promotionService.applyPromotion(promoId, new BigDecimal("100"), "user1");
        assertNull(result.getPromotionId());
    }

    @Test
    void testMonthlyPromotionSwitching() {
        Promotion currentMonthPromo = Promotion.builder().validMonth(String.format("%d-%02d", NOW.getYear(), NOW.getMonthValue())).build();
        Promotion oldMonthPromo = Promotion.builder().validMonth("2020-01").build();

        assertTrue(promotionService.isCurrentMonth(currentMonthPromo));
        assertFalse(promotionService.isCurrentMonth(oldMonthPromo));
    }

    @Test
    void testPromotionEnforcesOneTimeUsageGlobally() {
        String promoId = "ANY_PROMO";
        Promotion p = Promotion.builder()
                .id(promoId)
                .usageType(UsageType.MULTI_USE) // Even if it says MULTI_USE
                .startDate(NOW.minusDays(1))
                .endDate(NOW.plusDays(1))
                .discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10"))
                .currentUsage(0)
                .build();

        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(p));
        // User has already used it
        when(orderRepository.existsByBuyerIdAndAppliedPromotionId("user1", promoId)).thenReturn(true);

        var result = promotionService.applyPromotion(promoId, new BigDecimal("100"), "user1");
        // Should be blocked regardless of UsageType
        assertNull(result.getPromotionId());
    }
}
