package com.example.marketplace.repository;

import com.example.marketplace.model.Promotion;
import com.example.marketplace.model.PromotionCreator;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionRepository extends MongoRepository<Promotion, String> {
    List<Promotion> findByCreatedBy(PromotionCreator createdBy);
    List<Promotion> findBySellerId(String sellerId);
}
