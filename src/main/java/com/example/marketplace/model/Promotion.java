package com.example.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "promotions")
public class Promotion {
    @Id
    private String id;
    private String name;
    private DiscountType discountType;
    private BigDecimal value;

    /** When this promotion becomes active */
    private LocalDateTime startDate;

    /** When this promotion expires */
    private LocalDateTime endDate;

    /** Timestamp when the promotion was created in the system */
    private LocalDateTime createdAt;

    private String scope;
    private Integer usageLimit;

    @Builder.Default
    private int currentUsage = 0;

    @Builder.Default
    private UsageType usageType = UsageType.ONE_TIME;

    private String validMonth;

    /** Who created this promotion: ADMIN, SELLER, or SYSTEM */
    private PromotionCreator createdBy;

    /** Email of the creator (Seller or Admin), null for system */
    private String sellerId;

    public boolean isValid(LocalDateTime now) {
        String currentMonth = now.getYear() + "-" + String.format("%02d", now.getMonthValue());
        boolean monthValid = validMonth == null || validMonth.equals(currentMonth);
        boolean startValid = startDate == null || now.isAfter(startDate);
        boolean endValid = endDate == null || now.isBefore(endDate);
        boolean usageValid = usageLimit == null || currentUsage < usageLimit;
        return monthValid && startValid && endValid && usageValid;
    }

    public void incrementUsage() {
        if (usageLimit != null && currentUsage >= usageLimit) {
            throw new RuntimeException("Promotion usage limit reached");
        }
        currentUsage++;
    }

    public int getCurrentUsageCount() {
        return currentUsage;
    }
}
