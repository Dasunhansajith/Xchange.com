package com.example.marketplace.dto;

import com.example.marketplace.model.DiscountType;
import com.example.marketplace.model.UsageType;
import com.example.marketplace.model.PromotionCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionDto {
    private String id;
    private String name;
    private DiscountType discountType;
    private BigDecimal value;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /** Timestamp when this promotion was created */
    private LocalDateTime createdAt;

    private String scope;
    private Integer usageLimit;

    /** How many times this promotion has been redeemed globally */
    private int currentUsage;

    private UsageType usageType;
    private String validMonth;

    /** ADMIN, SELLER, or SYSTEM */
    private PromotionCreator createdBy;

    /** Email of the creator (Seller or Admin), null for system */
    private String sellerId;

    /**
     * True if the promotion has expired or exceeded its limit.
     * Using explicit @JsonProperty to prevent Lombok naming the field "expired"
     * while Jackson serializes it as "isExpired" for the frontend.
     */
    @JsonProperty("isExpired")
    private boolean expired;

    /**
     * True if the currently logged-in buyer has already used this promotion.
     */
    @JsonProperty("isUsed")
    private boolean used;
}
