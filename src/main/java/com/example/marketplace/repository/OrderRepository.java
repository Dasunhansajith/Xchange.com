package com.example.marketplace.repository;

import com.example.marketplace.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.marketplace.model.TrackingStatus;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);
    Page<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId, Pageable pageable);
    long countByBuyerId(String buyerId);
    
    List<Order> findBySellerIdOrderByCreatedAtDesc(String sellerId);
    Page<Order> findBySellerIdOrderByCreatedAtDesc(String sellerId, Pageable pageable);
    
    List<Order> findByTrackingStatus(TrackingStatus status);
    Page<Order> findByTrackingStatus(TrackingStatus status, Pageable pageable);
    
    @Query("{ 'sellerId': ?0, 'createdAt': { $gte: ?1, $lte: ?2 }, 'status': { $in: ['COMPLETED', 'ACCEPTED'] } }")
    List<Order> findSellerSalesByDateRange(String sellerId, LocalDateTime startDate, LocalDateTime endDate);

    /** Checks if a buyer has already placed an order using a specific promotion. Replaces user_promotion_usage. */
    boolean existsByBuyerIdAndAppliedPromotionId(String buyerId, String appliedPromotionId);
}
