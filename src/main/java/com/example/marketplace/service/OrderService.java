package com.example.marketplace.service;

import com.example.marketplace.model.Order;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.User;
import com.example.marketplace.model.Notification;
import com.example.marketplace.model.Review;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.repository.NotificationRepository;
import com.example.marketplace.repository.ReviewRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    public Order placeSingleOrder(String email, String productId, int quantity, String shippingAddress,
            String buyerName, String buyerPhone) {
        System.out.println("Placing single order for buyer: " + email + ", product: " + productId);

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            throw new RuntimeException("Product not found: " + productId);
        }
        
        // Handle missing sellerId for older products
        if (product.getSellerId() == null && product.getShopId() != null) {
            userRepository.findByShopId(product.getShopId()).ifPresent(u -> {
                product.setSellerId(u.getEmail());
                productRepository.save(product);
            });
        }

        // Handle missing stockQuantity for older products
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(100);
            productRepository.save(product);
        }

        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + product.getStockQuantity());
        }

        if (product.getPrice() == null) {
            product.setPrice(BigDecimal.ZERO);
        }

        BigDecimal totalPrice = product.getPrice().multiply(new BigDecimal(quantity));

        Order order = Order.builder()
                .buyerId(email)
                .buyerName(buyerName)
                .buyerPhone(buyerPhone)
                .sellerId(product.getSellerId())
                .items(Collections.singletonList(Order.OrderItem.builder()
                        .productId(productId)
                        .name(product.getName())
                        .price(product.getPrice())
                        .quantity(quantity)
                        .build()))
                .productName(product.getName())
                .productImage(product.getImages() != null && !product.getImages().isEmpty() ? product.getImages().get(0) : null)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Deduct stock
        product.setStockQuantity(product.getStockQuantity() - quantity);
        if (product.getStockQuantity() == 0) {
            product.setStatus("SOLD");
        }
        productRepository.save(product);

        order.initTracking();
        Order saved = orderRepository.save(order);
        notifySellers(saved);
        return saved;
    }

    private void notifySellers(Order order) {
        if (order.getSellerId() == null)
            return;
        notificationRepository.save(Notification.builder()
                .recipientId(order.getSellerId())
                .senderId(order.getBuyerId())
                .title("New Order Received")
                .message("You have received a new order for " + order.getProductName())
                .type("NEW_ORDER")
                .relatedId(order.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public Order acceptOrder(String orderId, String sellerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getSellerId().equals(sellerEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        order.setStatus("ACCEPTED");
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Notify Buyer
        notificationRepository.save(Notification.builder()
                .recipientId(order.getBuyerId())
                .senderId(sellerEmail)
                .title("Order Accepted")
                .message("Your order for " + order.getItems().get(0).getName() + " has been accepted by the seller.")
                .type("ORDER_ACCEPTED")
                .relatedId(order.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());

        return savedOrder;
    }

    public Order declineOrder(String orderId, String sellerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getSellerId().equals(sellerEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        order.setStatus("DECLINED");
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Notify Buyer
        notificationRepository.save(Notification.builder()
                .recipientId(order.getBuyerId())
                .senderId(sellerEmail)
                .title("Order Declined")
                .message("Your order for " + order.getItems().get(0).getName() + " has been declined by the seller.")
                .type("ORDER_DECLINED")
                .relatedId(order.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());

        return savedOrder;
    }

    public List<Order> getOrdersForSeller(String sellerEmail) {
        System.out.println("Fetching orders for seller: " + sellerEmail);
        List<Order> orders = orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerEmail);
        System.out.println("Found " + orders.size() + " orders for " + sellerEmail);
        return orders;
    }

    public Order placeOrderFromWishlist(String email, String shippingAddress) {
        System.out.println("Placing wishlist order for buyer: " + email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Set<String> wishlist = user.getWishlist();
        if (wishlist == null || wishlist.isEmpty()) {
            throw new RuntimeException("Wishlist is empty");
        }

        // OPTIMIZATION: Batch fetch all products instead of individual queries
        List<String> itemIds = new ArrayList<>(wishlist);
        
        // Batch fetch products
        List<Product> allProducts = productRepository.findByIdIn(itemIds);
        Map<String, Product> productMap = allProducts.stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        List<Order.OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        String firstSellerId = null;
        String firstProductImage = null;
        
        // Track items to update for batch save
        List<Product> productsToUpdate = new ArrayList<>();

        for (String itemId : wishlist) {
            // Check Product from map (no DB query)
            Product product = productMap.get(itemId);
            if (product != null) {
                // Repair older products
                if (product.getSellerId() == null && product.getShopId() != null) {
                    java.util.Optional<User> shopOwner = userRepository.findByShopId(product.getShopId());
                    if (shopOwner.isPresent()) {
                        product.setSellerId(shopOwner.get().getEmail());
                    }
                }
                if (product.getStockQuantity() == null) {
                    product.setStockQuantity(100);
                }

                if (product.getStockQuantity() >= 1) {
                    orderItems.add(Order.OrderItem.builder()
                            .productId(itemId)
                            .name(product.getName())
                            .price(product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO)
                            .quantity(1)
                            .build());
                    totalPrice = totalPrice.add(product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO);
                    if (firstSellerId == null)
                        firstSellerId = product.getSellerId();
                    if (firstProductImage == null)
                        firstProductImage = product.getImages() != null && !product.getImages().isEmpty() ? product.getImages().get(0) : null;

                    // Deduct stock
                    product.setStockQuantity(product.getStockQuantity() - 1);
                    if (product.getStockQuantity() == 0)
                        product.setStatus("SOLD");
                    productsToUpdate.add(product);  // Track for batch save
                }
            }
        }

        if (orderItems.isEmpty()) {
            throw new RuntimeException("No valid items found in wishlist or items are out of stock");
        }

        Order order = Order.builder()
                .buyerId(email)
                .sellerId(firstSellerId)
                .items(orderItems)
                .productName(orderItems.size() > 1
                        ? orderItems.get(0).getName() + " & " + (orderItems.size() - 1) + " others"
                        : orderItems.get(0).getName())
                .productImage(firstProductImage)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        order.initTracking();

        // OPTIMIZATION: Batch save products instead of individual saves
        if (!productsToUpdate.isEmpty()) {
            productRepository.saveAll(productsToUpdate);
        }

        // Clear wishlist
        user.getWishlist().clear();
        userRepository.save(user);

        Order saved = orderRepository.save(order);
        notifySellers(saved);
        return saved;
    }

    public Order submitReview(String orderId, String buyerEmail, Integer rating, String review) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getBuyerId().equals(buyerEmail)) {
            throw new RuntimeException("Only the buyer can review this order");
        }

        String productId = order.getItems().get(0).getProductId();

        // Prevent duplicate reviews — update existing if it exists
        java.util.Optional<Review> existing = reviewRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            // Delegate to edit path
            return editReview(orderId, buyerEmail, rating, review);
        }

        order.setRating(rating);
        order.setReview(review);
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Save to the Review collection
        Review reviewDoc = Review.builder()
                .orderId(orderId)
                .productId(productId)
                .productName(order.getProductName())
                .buyerId(buyerEmail)
                .buyerName(order.getBuyerName())
                .rating(rating)
                .comment(review)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        reviewRepository.save(reviewDoc);

        // Recalculate product rating from scratch
        recalculateProductRating(productId);

        return savedOrder;
    }

    public Order editReview(String orderId, String buyerEmail, Integer rating, String comment) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getBuyerId().equals(buyerEmail)) {
            throw new RuntimeException("Unauthorized: only the buyer can edit this review");
        }

        String productId = order.getItems().get(0).getProductId();

        // Update order fields
        order.setRating(rating);
        order.setReview(comment);
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Update the Review document
        Review reviewDoc = reviewRepository.findByOrderId(orderId)
                .orElseGet(() -> Review.builder()
                        .orderId(orderId)
                        .productId(productId)
                        .productName(order.getProductName())
                        .buyerId(buyerEmail)
                        .buyerName(order.getBuyerName())
                        .createdAt(LocalDateTime.now())
                        .build());

        reviewDoc.setRating(rating);
        reviewDoc.setComment(comment);
        reviewDoc.setUpdatedAt(LocalDateTime.now());
        reviewRepository.save(reviewDoc);

        // Recalculate product rating from scratch
        recalculateProductRating(productId);

        return savedOrder;
    }

    public void deleteReview(String orderId, String buyerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getBuyerId().equals(buyerEmail)) {
            throw new RuntimeException("Unauthorized: only the buyer can delete this review");
        }

        String productId = order.getItems().get(0).getProductId();

        // Clear review fields on the order
        order.setRating(null);
        order.setReview(null);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Remove the Review document
        reviewRepository.findByOrderId(orderId).ifPresent(reviewRepository::delete);

        // Recalculate product rating from scratch
        recalculateProductRating(productId);
    }

    private void recalculateProductRating(String productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return;

        // OPTIMIZATION: Calculate ratings efficiently
        java.util.List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        int count = reviews.size();
        double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        product.setReviewCount(count);
        product.setAverageRating(count > 0 ? Math.round(average * 10.0) / 10.0 : 0.0);
        productRepository.save(product);
    }

    public List<Order> getMyOrders(String email) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(email);
    }

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
    
    @Deprecated(forRemoval = true) // Use getAllOrders(Pageable) instead
    public List<Order> getAllOrders() {
        // Return first 100 for backward compatibility
        return orderRepository.findAll(PageRequest.of(0, 100)).getContent();
    }

    public com.example.marketplace.dto.SalesReportDTO getSalesReport(String sellerId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Order> orders = orderRepository.findSellerSalesByDateRange(sellerId, startDate, endDate);
        
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<com.example.marketplace.dto.SalesReportDTO.OrderSummary> orderSummaries = orders.stream()
                .map(order -> com.example.marketplace.dto.SalesReportDTO.OrderSummary.builder()
                        .orderId(order.getId())
                        .productName(order.getProductName())
                        .quantity(order.getItems() != null && !order.getItems().isEmpty() 
                                ? order.getItems().get(0).getQuantity() 
                                : 0)
                        .price(order.getItems() != null && !order.getItems().isEmpty() 
                                ? order.getItems().get(0).getPrice() 
                                : BigDecimal.ZERO)
                        .totalPrice(order.getTotalPrice())
                        .buyerName(order.getBuyerName())
                        .status(order.getStatus())
                        .dateCreated(order.getCreatedAt())
                        .build())
                .toList();

        return com.example.marketplace.dto.SalesReportDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalOrders(orders.size())
                .totalRevenue(totalRevenue)
                .orders(orderSummaries)
                .build();
    }
}

