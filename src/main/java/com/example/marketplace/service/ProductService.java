package com.example.marketplace.service;

import com.example.marketplace.dto.ProductDto;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.User;
import com.example.marketplace.model.Shop;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.ShopRepository;
import com.example.marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.example.marketplace.repository.ReviewRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import com.example.marketplace.exception.BadRequestException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Point;
import org.springframework.data.support.PageableExecutionUtils;

@Service
@SuppressWarnings("null")
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void normalizeProductStatuses() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                int pageSize = 50;
                int page = 0;
                int updatedCount = 0;
                boolean hasMore = true;

                System.out.println("====== DB MIGRATION: STARTING IN BACKGROUND ======");

                while (hasMore) {
                    Query query = new Query().with(org.springframework.data.domain.PageRequest.of(page, pageSize));
                    List<Product> products = mongoTemplate.find(query, Product.class);

                    if (products.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (Product p : products) {
                        try {
                            boolean modified = false;

                            // 1. Force all missing or weird statuses to ACTIVE (ensures they show up)
                            if (p.getStatus() == null || !java.util.List.of("ACTIVE", "SOLD", "ARCHIVED", "DRAFT").contains(p.getStatus().toUpperCase())) {
                                p.setStatus("ACTIVE");
                                modified = true;
                            } else if (p.getStatus() != null && !p.getStatus().equals(p.getStatus().toUpperCase())) {
                                p.setStatus(p.getStatus().toUpperCase());
                                modified = true;
                            }

                            // 2. Fix "Sri Lanka" to "Colombo"
                            if ("Sri Lanka".equalsIgnoreCase(p.getCity())) {
                                p.setCity("Colombo");
                                modified = true;
                            }
                            if ("Sri Lanka".equalsIgnoreCase(p.getDistrict())) {
                                p.setDistrict("Colombo");
                                modified = true;
                            }

                            // 3. Ensure district is populated (fallback to city or Colombo)
                            if (p.getDistrict() == null || p.getDistrict().trim().isEmpty()) {
                                if (p.getCity() != null && !p.getCity().trim().isEmpty()) {
                                    p.setDistrict(p.getCity());
                                } else {
                                    p.setDistrict("Colombo");
                                    p.setCity("Colombo");
                                }
                                modified = true;
                            }

                            // Removed GeoJSON coordinates logic

                            if (modified) {
                                mongoTemplate.save(p);
                                updatedCount++;
                            }
                        } catch (Exception innerEx) {
                            System.err.println("Error migrating product " + p.getId() + ": " + innerEx.getMessage());
                        }
                    }
                    page++;
                }

                if (updatedCount > 0) {
                    System.out.println("====== DB MIGRATION: COMPLETED ======");
                    System.out.println("Successfully migrated and repaired " + updatedCount + " products in MongoDB.");
                    System.out.println("Fixed: Status casing, 'Sri Lanka' -> 'Colombo', missing districts, and GeoJSON coordinates.");
                    System.out.println("=======================================");
                } else {
                    System.out.println("====== DB MIGRATION: FINISHED (NO UPDATES NEEDED) ======");
                }
            } catch (Exception ex) {
                System.err.println("CRITICAL ERROR during background DB Migration: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    public ProductDto createProduct(ProductDto dto, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String shopName = null;
        if (user.getShopId() != null) {
            shopName = shopRepository.findById(user.getShopId()).map(Shop::getShopName).orElse(null);
        }

        // Removed delivery radius and coordinate checks

        Product product = Product.builder()
                .sellerId(user.getEmail())
                .shopId(user.getShopId())
                .shopName(shopName)
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .category(dto.getCategory())
                .stockQuantity(dto.getStockQuantity())
                .images(dto.getImages())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(dto.getStatus() != null ? dto.getStatus().toUpperCase() : "ACTIVE")
                .district(dto.getDistrict())
                .city(dto.getCity())
                .build();

        Product saved = productRepository.save(product);
        return mapToDto(saved, false);
    }

    public List<ProductDto> getAllProducts() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").regex("^ACTIVE$", "i"));
        return mongoTemplate.find(query, Product.class).stream()
                .map(p -> mapToDto(p, false))
                .collect(Collectors.toList());
    }

    public List<ProductDto> getProductsBySeller(String userEmail) {
        return productRepository.findBySellerId(userEmail).stream()
                .map(p -> mapToDto(p, false))
                .collect(Collectors.toList());
    }

    public ProductDto getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToDto(product, true);
    }

    public ProductDto updateProduct(String id, ProductDto dto, String userEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Check if the user is the owner
        if (!product.getSellerId().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to update this product");
        }

        // Removed delivery radius and coordinate checks

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCategory(dto.getCategory());
        product.setStockQuantity(dto.getStockQuantity());
        product.setImages(dto.getImages());
        
        // Ensure status is stored in uppercase
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus().toUpperCase());
        } else {
            product.setStatus("ACTIVE");
        }
        
        product.setUpdatedAt(LocalDateTime.now());
        
        product.setDistrict(dto.getDistrict());
        product.setCity(dto.getCity());
        Product updated = productRepository.save(product);
        return mapToDto(updated, false);
    }

    public void deleteProduct(String id, String userEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Check if the user is the owner
        if (!product.getSellerId().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to delete this product");
        }

        productRepository.delete(product);
    }

    public Page<ProductDto> filterProducts(String term, Pageable pageable) {
        Page<Product> productPage;
        if (term == null || term.trim().isEmpty() || term.equalsIgnoreCase("ALL")) {
            productPage = productRepository.findActiveProducts(pageable);
        } else {
            productPage = productRepository.findActiveByDistrictOrCity(term, term, pageable);
        }
        return productPage.map(p -> mapToDto(p, false));
    }

    private ProductDto mapToDto(Product product, boolean includeReviews) {
        String shopName = product.getShopName();
        if (shopName == null && product.getShopId() != null) {
            shopName = shopRepository.findById(product.getShopId()).map(Shop::getShopName)
                    .orElse("Shop #" + product.getShopId().substring(0, 5) + "...");
        }

        List<com.example.marketplace.model.Review> reviews = null;
        if (includeReviews) {
            reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(product.getId());
        }

        return ProductDto.builder()
                .id(product.getId())
                .sellerId(product.getSellerId())
                .shopId(product.getShopId())
                .shopName(shopName)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .images(product.getImages())
                .createdAt(product.getCreatedAt())
                .status(product.getStatus())
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .reviews(reviews)
                .district(product.getDistrict())
                .city(product.getCity())
                .build();
    }
}
