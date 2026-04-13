package com.example.marketplace.repository;

import com.example.marketplace.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import org.springframework.data.mongodb.repository.Query;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findBySellerId(String sellerId);

    List<Product> findByShopId(String shopId);

    List<Product> findByCategory(String category);

    @Query("{ 'status': { $regex: '^ACTIVE$', $options: 'i' } }")
    Page<Product> findActiveProducts(Pageable pageable);

    @Query("{ 'status': { $regex: '^ACTIVE$', $options: 'i' }, $or: [ { 'district': { $regex: ?0, $options: 'i' } }, { 'city': { $regex: ?1, $options: 'i' } } ] }")
    Page<Product> findActiveByDistrictOrCity(String district, String city, Pageable pageable);
}
