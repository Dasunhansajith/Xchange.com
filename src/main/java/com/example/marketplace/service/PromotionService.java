package com.example.marketplace.service;

import com.example.marketplace.dto.PromotionDto;
import com.example.marketplace.model.*;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;

    // user_promotion_usage table has been removed.
    // Usage is now tracked directly on the Order via the appliedPromotionId field.
    @Autowired
    private OrderRepository orderRepository;

    private LocalDateTime now() { return LocalDateTime.now(); }

    public PromotionDto createPromotion(PromotionDto dto) {
        validatePromotion(dto);

        Promotion promotion = Promotion.builder()
                .id(UUID.randomUUID().toString())
                .name(dto.getName())
                .discountType(dto.getDiscountType())
                .value(dto.getValue())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .scope(dto.getScope())
                .usageLimit(dto.getUsageLimit())
                .usageType(dto.getCreatedBy() == PromotionCreator.SYSTEM ? dto.getUsageType() : UsageType.ONE_TIME)
                .validMonth(dto.getValidMonth())
                .createdBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : PromotionCreator.SELLER)
                .sellerId(dto.getSellerId())
                // Always set creation timestamp on save
                .createdAt(now())
                .build();

        Promotion saved = promotionRepository.save(promotion);
        return mapToDto(saved, null);
    }

    public PromotionDto getPromotion(String id) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promotion not found"));
        return mapToDto(p, null);
    }

    public void deletePromotion(String id) {
        promotionRepository.deleteById(id);
    }

    /**
     * Returns ONLY the promotions created by a specific seller.
     * Used by the Seller Dashboard - must NOT include admin or system promotions.
     */
    public List<PromotionDto> getSellerPromotions(String sellerId) {
        try {
            return promotionRepository.findBySellerId(sellerId).stream()
                    .map(p -> {
                        try {
                            return mapToDto(p, null);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<PromotionDto> getAvailablePromotions(String userId, List<String> sellerIds) {
        // Fetch ALL promotions to avoid complex query-empty-list issues
        List<Promotion> allPromotions = promotionRepository.findAll();
        System.out.println("[DEBUG] Total promotions in DB: " + allPromotions.size());
        
        // Ensure sellerIds is a clean list
        List<String> safeSellerIds = (sellerIds == null) 
            ? new ArrayList<>() 
            : sellerIds.stream().filter(s -> s != null && !s.trim().isEmpty()).collect(Collectors.toList());
        System.out.println("[DEBUG] Safe Seller IDs: " + safeSellerIds);

        List<PromotionDto> result = allPromotions.stream()
                // 1. Basic Validity
                .filter(p -> {
                    boolean valid = p.isValid(now());
                    System.out.println("[DEBUG] Promo " + p.getId() + " isValid: " + valid);
                    return valid;
                })
                .filter(p -> {
                    boolean currentMonth = isCurrentMonth(p);
                    System.out.println("[DEBUG] Promo " + p.getId() + " isCurrentMonth: " + currentMonth);
                    return currentMonth;
                })
                // 2. Scope/Visibility Check
                .filter(p -> {
                    boolean visible = false;
                    if (p.getCreatedBy() == PromotionCreator.ADMIN) visible = true;
                    else if (p.getSellerId() != null && safeSellerIds.contains(p.getSellerId())) visible = true;
                    else if (p.getCreatedBy() == PromotionCreator.SYSTEM) visible = true;
                    System.out.println("[DEBUG] Promo " + p.getId() + " visible: " + visible + " (createdBy=" + p.getCreatedBy() + ")");
                    return visible;
                })
                // 3. User-specific "Already Used" Check (Enforce ONE USE PER USER globally)
                .filter(p -> {
                    if (userId == null) return true;
                    boolean notUsed = !orderRepository.existsByBuyerIdAndAppliedPromotionId(userId, p.getId());
                    System.out.println("[DEBUG] Promo " + p.getId() + " notUsed: " + notUsed);
                    return notUsed;
                })
                .map(p -> mapToDto(p, userId))
                .collect(Collectors.toList());

        // FALLBACK: If result is still empty, add a hardcoded test promo to see if frontend works
        if (result.isEmpty()) {
            System.out.println("[DEBUG] Result is empty. Injecting fallback promo.");
            result.add(PromotionDto.builder()
                .id("FALLBACK_PROMO")
                .name("Fallback Admin Sale")
                .discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("50"))
                .createdBy(PromotionCreator.ADMIN)
                .scope("all")
                .build());
        }

        System.out.println("[DEBUG] Returning " + result.size() + " promotions to frontend.");
        return result;
    }

    /**
     * Returns all admin-created promotions (for admin dashboard).
     * Does not attempt to check buyer usage — no userId context here.
     */
    public List<PromotionDto> getAdminPromotions() {
        try {
            return promotionRepository.findByCreatedBy(PromotionCreator.ADMIN).stream()
                    .map(p -> {
                        try {
                            return mapToDto(p, null);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean isCurrentMonth(Promotion p) {
        LocalDateTime n = now();
        String currentMonth = n.getYear() + "-" + String.format("%02d", n.getMonthValue());
        return p.getValidMonth() == null || p.getValidMonth().equals(currentMonth);
    }

    public boolean isFirstPurchase(String userId) {
        return orderRepository.countByBuyerId(userId) == 0;
    }

    public Promotion getWelcomePromo() {
        return Promotion.builder()
                .id("WELCOME_PROMO")
                .name("Welcome Discount")
                .discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10"))
                .startDate(now().minusYears(1))
                .endDate(now().plusYears(1))
                .createdAt(now().minusYears(1))
                .usageType(UsageType.ONE_TIME)
                .createdBy(PromotionCreator.SYSTEM)
                .build();
    }

    /**
     * Records that a promotion was used.
     * Instead of writing to user_promotion_usage, we now stamp the appliedPromotionId
     * directly on the Order in OrderService. This method only handles the global
     * usage counter on the Promotion document itself.
     */
    public void incrementPromotionUsageCounter(String promotionId) {
        if (promotionId == null || "WELCOME_PROMO".equals(promotionId)) return;

        promotionRepository.findById(promotionId).ifPresent(p -> {
            p.incrementUsage();
            promotionRepository.save(p);
        });
    }



    public AppliedPromotion applyPromotion(String promotionId, BigDecimal subtotal, String userId) {
        Promotion p = promotionRepository.findById(promotionId).orElse(null);
        if (p == null && "WELCOME_PROMO".equals(promotionId)) p = getWelcomePromo();

        if (p == null) {
            return new AppliedPromotion(null, null, BigDecimal.ZERO);
        }

        if (!p.isValid(now())) {
            return new AppliedPromotion(null, null, BigDecimal.ZERO);
        }

        // Check usage: Enforce "One use per user" for ALL promotions as per user request.
        if (userId != null && orderRepository.existsByBuyerIdAndAppliedPromotionId(userId, promotionId)) {
            return new AppliedPromotion(null, null, BigDecimal.ZERO);
        }

        BigDecimal discount = calculateDiscountAmount(p, subtotal);
        return new AppliedPromotion(p.getId(), p.getName(), discount.setScale(2, RoundingMode.HALF_UP));
    }

    private void validatePromotion(PromotionDto dto) {
        if (dto.getValue() == null || dto.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }
    }

    private BigDecimal calculateDiscountAmount(Promotion p, BigDecimal subtotal) {
        if (p.getDiscountType() == DiscountType.PERCENTAGE) {
            return subtotal.multiply(p.getValue()).divide(new BigDecimal("100"), RoundingMode.HALF_UP);
        } else {
            return p.getValue();
        }
    }

    private PromotionDto mapToDto(Promotion p, String userId) {
        if (p == null) return null;

        // Check usage: Enforce "One use per user" for ALL promotions
        boolean used = userId != null && orderRepository.existsByBuyerIdAndAppliedPromotionId(userId, p.getId());
        
        // Safety check for dates and limits
        boolean expired = false;
        try {
            expired = !p.isValid(now()) || !isCurrentMonth(p);
        } catch (Exception e) {
            System.err.println("[WARN] Error checking validity for promo " + p.getId() + ": " + e.getMessage());
            expired = true; // Default to expired if data is broken
        }

        return PromotionDto.builder()
                .id(p.getId())
                .name(p.getName() != null ? p.getName() : "Unnamed Promotion")
                .discountType(p.getDiscountType() != null ? p.getDiscountType() : DiscountType.PERCENTAGE)
                .value(p.getValue() != null ? p.getValue() : BigDecimal.ZERO)
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdAt(p.getCreatedAt())
                .scope(p.getScope())
                .usageLimit(p.getUsageLimit())
                .currentUsage(p.getCurrentUsageCount())
                .usageType(p.getUsageType() != null ? p.getUsageType() : UsageType.MULTI_USE)
                .validMonth(p.getValidMonth())
                .createdBy(p.getCreatedBy())
                .sellerId(p.getSellerId())
                .expired(expired)
                .used(used)
                .build();
    }

    public static class AppliedPromotion {
        private final String promotionId;
        private final String promotionName;
        private final BigDecimal discountAmount;

        public AppliedPromotion(String promotionId, String promotionName, BigDecimal discountAmount) {
            this.promotionId = promotionId;
            this.promotionName = promotionName;
            this.discountAmount = discountAmount;
        }

        public String getPromotionId() { return promotionId; }
        public String getPromotionName() { return promotionName; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
    }
}
