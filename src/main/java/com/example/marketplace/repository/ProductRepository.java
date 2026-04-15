package com.example.marketplace.repository;

import com.example.marketplace.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findBySellerId(String sellerId);
    Page<Product> findBySellerId(String sellerId, Pageable pageable);

    List<Product> findByShopId(String shopId);

    List<Product> findByCategory(String category);
    
    // Filter by status to show only active products
    Page<Product> findByStatus(String status, Pageable pageable);
    
    // Batch query for improved performance - avoid N+1 queries
    List<Product> findByIdIn(List<String> ids);
}
