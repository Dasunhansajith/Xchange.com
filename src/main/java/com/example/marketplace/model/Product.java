package com.example.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
@CompoundIndexes({
    @CompoundIndex(name = "idx_sellerId_status", def = "{'sellerId': 1, 'status': 1}"),
    @CompoundIndex(name = "idx_shopId_status", def = "{'shopId': 1, 'status': 1}"),
    @CompoundIndex(name = "idx_category_status", def = "{'category': 1, 'status': 1}")
})
public class Product {
    @Id
    private String id;
    @Indexed
    private String sellerId; // Link to the user (email)
    @Indexed
    private String shopId; // Link to the shop
    private String shopName; // Denormalized for display
    private String name;
    private String description;
    private BigDecimal price;
    @Indexed
    private String category;
    private Integer stockQuantity;
    @Builder.Default
    private List<String> images = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, SOLD, DRAFT, ARCHIVED
    @Builder.Default
    private Double averageRating = 0.0;
    @Builder.Default
    private Integer reviewCount = 0;

    // --- Location-Based Filtering Fields ---
    @org.springframework.data.mongodb.core.index.Indexed
    private String district;
    @org.springframework.data.mongodb.core.index.Indexed
    private String city;
}
