package com.example.marketplace.repository;

import com.example.marketplace.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    
    // ===== BASIC QUERIES =====
    List<Product> findBySellerId(String sellerId);
    Page<Product> findBySellerId(String sellerId, Pageable pageable);
    
    List<Product> findByShopId(String shopId);
    
    List<Product> findByCategory(String category);
    
    // Filter by status - uses index on 'status' field
    Page<Product> findByStatus(String status, Pageable pageable);
    
    // Batch query for improved performance - avoid N+1 queries
    List<Product> findByIdIn(List<String> ids);
    
    // ===== LOCATION-BASED FILTERS (OPTIMIZED) =====
    // These use standard MongoDB queries that can leverage indexes
    // Create indexes: @Indexed on status, district, city fields in Product model
    
    /**
     * Find active products by district (case-insensitive exact match)
     * Performance: Uses compound index on (status, district)
     */
    Page<Product> findByStatusAndDistrictIgnoreCase(String status, String district, Pageable pageable);
    
    /**
     * Find active products by city (case-insensitive exact match)
     * Performance: Uses compound index on (status, city)
     */
    Page<Product> findByStatusAndCityIgnoreCase(String status, String city, Pageable pageable);
    
    /**
     * Find active products by both district AND city (case-insensitive exact match)
     * Performance: Uses compound index on (status, district, city)
     */
    Page<Product> findByStatusAndDistrictIgnoreCaseAndCityIgnoreCase(
        String status, String district, String city, Pageable pageable);
    
    /**
     * Find active products by district OR city (case-insensitive exact match)
     * Note: MongoDB can only use one index per query, so this may be slower than AND queries
     * Consider using text search or aggregation pipeline for complex OR queries
     */
    @Query("{ 'status': ?0, $or: [ { 'district': { $regex: ?1, $options: 'i' } }, { 'city': { $regex: ?2, $options: 'i' } } ] }")
    Page<Product> findByStatusAndDistrictOrCity(
        String status, String district, String city, Pageable pageable);
    
    // ===== ADVANCED FILTERS (MULTIPLE CRITERIA) =====
    
    /**
     * Find products with multiple filters (category + location)
     * Performance: Use compound indexes based on common filter combinations
     */
    @Query("{ 'status': ?0, 'category': ?1, 'district': { $regex: ?2, $options: 'i' } }")
    Page<Product> findActiveByCategoryAndDistrict(
        String status, String category, String district, Pageable pageable);
    
    /**
     * Find products by price range and location
     */
    @Query("{ 'status': ?0, 'price': { $gte: ?1, $lte: ?2 }, 'district': { $regex: ?3, $options: 'i' } }")
    Page<Product> findActiveByPriceRangeAndDistrict(
        String status, Double minPrice, Double maxPrice, String district, Pageable pageable);
    
    // ===== BULK OPERATIONS =====
    
    /**
     * Bulk update product status (for admin operations)
     */
    @Query("{ 'sellerId': ?0 }")
    List<Product> findAllBySellerId(String sellerId);
    
    /**
     * Count active products by district (for analytics)
     */
    int countByStatusAndDistrictIgnoreCase(String status, String district);
}