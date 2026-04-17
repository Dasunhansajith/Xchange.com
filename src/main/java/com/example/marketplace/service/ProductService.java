package com.example.marketplace.service;

import com.example.marketplace.dto.ProductDto;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.User;
import com.example.marketplace.model.Shop;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.ShopRepository;
import com.example.marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.example.marketplace.repository.ReviewRepository;

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
    private NotificationService notificationService;

    public ProductDto createProduct(ProductDto dto, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String shopName = null;
        if (user.getShopId() != null) {
            shopName = shopRepository.findById(user.getShopId()).map(Shop::getShopName).orElse(null);
        }

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
                .status("ACTIVE")
                .district(dto.getDistrict())      // KEPT from feature branch
                .city(dto.getCity())              // KEPT from feature branch
                .build();

        Product saved = productRepository.save(product);
        return mapToDto(saved, false);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> getAllProducts(Pageable pageable) {
        // Filter for ACTIVE products only to avoid returning archived/sold items
        return productRepository.findByStatus("ACTIVE", pageable)  // IMPROVED: filter at DB level
                .map(p -> mapToDto(p, false));
    }

    public Page<ProductDto> getProductsBySeller(String userEmail, Pageable pageable) {
        return productRepository.findBySellerId(userEmail, pageable)
                .map(p -> mapToDto(p, false));
    }
    
    @Deprecated(forRemoval = true) // Use getProductsBySeller(userEmail, Pageable) instead
    public List<ProductDto> getProductsBySeller(String userEmail) {
        // Return first 100 for backward compatibility
        return productRepository.findBySellerId(userEmail, PageRequest.of(0, 100)).stream()
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

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCategory(dto.getCategory());
        product.setStockQuantity(dto.getStockQuantity());
        product.setImages(dto.getImages());
        product.setStatus(dto.getStatus());
        product.setUpdatedAt(LocalDateTime.now());
        product.setDistrict(dto.getDistrict());    // KEPT from feature branch
        product.setCity(dto.getCity());            // KEPT from feature branch

        Product updated = productRepository.save(product);
        return mapToDto(updated, false);
    }

    public void deleteProduct(String id, Authentication auth) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Check if user is ADMIN or the owner
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin && !product.getSellerId().equals(auth.getName())) {
            throw new RuntimeException("You are not authorized to delete this product");
        }

        // Send notification to seller if admin is deleting the product
        if (isAdmin && !product.getSellerId().equals(auth.getName())) {
            String sellerEmail = product.getSellerId();
            User seller = userRepository.findByEmail(sellerEmail).orElse(null);
            
            if (seller != null) {
                notificationService.createNotification(
                        sellerEmail,
                        "Product Removed by Admin",
                        "Your product '" + product.getName()
                                + "' has been removed from the marketplace by an administrator.",
                        "PRODUCT_DELETED_BY_ADMIN",
                        product.getId());
            }
        }

        productRepository.delete(product);
    }

    // KEPT and IMPROVED from feature branch - location filtering
    public Page<ProductDto> filterProducts(String district, String city, Pageable pageable) {
        String status = "ACTIVE";
        boolean hasDistrict = district != null && !district.trim().isEmpty() && !district.equalsIgnoreCase("ALL");
        boolean hasCity = city != null && !city.trim().isEmpty() && !city.equalsIgnoreCase("ALL");

        Page<Product> productPage;

        if (!hasDistrict && !hasCity) {
            // No location filters - return all active products
            productPage = productRepository.findByStatus(status, pageable);
        } else if (hasDistrict && hasCity) {
            // Both district AND city provided - most specific
            productPage = productRepository.findByStatusAndDistrictIgnoreCaseAndCityIgnoreCase(
                status, district, city, pageable);
        } else if (hasDistrict) {
            // Only district provided
            productPage = productRepository.findByStatusAndDistrictIgnoreCase(status, district, pageable);
        } else {
            // Only city provided
            productPage = productRepository.findByStatusAndCityIgnoreCase(status, city, pageable);
        }

        return productPage.map(p -> mapToDto(p, false));
    }

    private ProductDto mapToDto(Product product, boolean includeReviews) {
        // Use product's denormalized shopName instead of querying database (prevents N+1 queries)
        String shopName = product.getShopName();
        if (shopName == null || shopName.trim().isEmpty()) {
            // Only fallback to lookup if absolutely necessary
            shopName = product.getShopId() != null
                    ? ("Shop #" + product.getShopId().substring(0, Math.min(5, product.getShopId().length())) + "...")
                    : "Unknown Shop";
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
                .district(product.getDistrict())    // ADDED from feature branch
                .city(product.getCity())            // ADDED from feature branch
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .reviews(reviews)
                .build();
    }
}