package com.example.marketplace.config;

import com.example.marketplace.model.*;
import com.example.marketplace.repository.PromotionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initPromotions(PromotionRepository repository) {
        return args -> {
            // Always upsert the system promotions so they stay correct even if
            // the DB already existed before the createdBy field was added.

            // 1. Site-wide Admin Promotion
            if (!repository.existsById("ADMIN_SALE_2026")) {
                repository.save(Promotion.builder()
                        .id("ADMIN_SALE_2026")
                        .name("Site-wide Summer Sale")
                        .discountType(DiscountType.PERCENTAGE)
                        .value(new BigDecimal("15"))
                        .startDate(LocalDateTime.now().minusDays(1))
                        .endDate(LocalDateTime.now().plusMonths(3))
                        .createdBy(PromotionCreator.ADMIN)
                        .sellerId("admin@xchange.com") // Admin email
                        .createdAt(LocalDateTime.now())
                        .usageType(UsageType.ONE_TIME)
                        .build()); // No validMonth = always visible, not locked to one month
                System.out.println("[DataInitializer] Seeded ADMIN_SALE_2026");
            } else {
                // Patch existing document to ensure createdBy and sellerId are set
                repository.findById("ADMIN_SALE_2026").ifPresent(p -> {
                    boolean updated = false;
                    if (p.getCreatedBy() == null) {
                        p.setCreatedBy(PromotionCreator.ADMIN);
                        updated = true;
                    }
                    if (p.getSellerId() == null) {
                        p.setSellerId("admin@xchange.com");
                        updated = true;
                    }
                    if (updated) {
                        repository.save(p);
                        System.out.println("[DataInitializer] Patched ADMIN_SALE_2026 fields");
                    }
                });
            }

            // 2. Category-specific Promotion (Electronics)
            if (!repository.existsById("ELECTRONICS_SALE")) {
                repository.save(Promotion.builder()
                        .id("ELECTRONICS_SALE")
                        .name("Tech Bonanza - Electronics")
                        .discountType(DiscountType.PERCENTAGE)
                        .value(new BigDecimal("10"))
                        .startDate(LocalDateTime.now().minusDays(5))
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .createdBy(PromotionCreator.ADMIN)
                        .sellerId("admin@xchange.com") // Admin email
                        .createdAt(LocalDateTime.now())
                        .scope("CATEGORY:Electronics")
                        .usageType(UsageType.ONE_TIME) // Enforce ONE_TIME
                        .build());
                System.out.println("[DataInitializer] Seeded ELECTRONICS_SALE");
            }

            // Aggressive patch: Ensure ALL promotions in the database have a creator email and are ONE_TIME
            repository.findAll().forEach(p -> {
                boolean updated = false;
                
                // 1. Enforce ONE_TIME for non-system promotions
                if (p.getCreatedBy() != PromotionCreator.SYSTEM && p.getUsageType() != UsageType.ONE_TIME) {
                    p.setUsageType(UsageType.ONE_TIME);
                    updated = true;
                }
                
                // 2. Ensure creatorId (sellerId) is set for Admin promos
                if (p.getCreatedBy() == PromotionCreator.ADMIN && p.getSellerId() == null) {
                    p.setSellerId("admin@xchange.com");
                    updated = true;
                }
                
                // 3. Ensure createdBy is set (Safety check)
                if (p.getCreatedBy() == null) {
                    if (p.getSellerId() != null) {
                        p.setCreatedBy(PromotionCreator.SELLER);
                    } else {
                        p.setCreatedBy(PromotionCreator.ADMIN);
                        p.setSellerId("admin@xchange.com");
                    }
                    updated = true;
                }
                
                if (updated) {
                    repository.save(p);
                    System.out.println("[DataInitializer] Patched promotion: " + p.getId());
                }
            });

            System.out.println("[DataInitializer] Promotion check and patch complete.");
        };
    }
}
