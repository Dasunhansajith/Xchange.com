package com.example.marketplace.repository;

import com.example.marketplace.model.UserPromotionUsage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPromotionUsageRepository extends MongoRepository<UserPromotionUsage, String> {
    List<UserPromotionUsage> findByUserId(String userId);
    Optional<UserPromotionUsage> findByUserIdAndPromotionId(String userId, String promotionId);
    boolean existsByUserIdAndPromotionId(String userId, String promotionId);
}
