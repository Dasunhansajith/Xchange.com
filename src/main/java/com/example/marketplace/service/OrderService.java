package com.example.marketplace.service;

import com.example.marketplace.dto.SalesReportDTO;
import com.example.marketplace.model.*;
import com.example.marketplace.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private MongoTemplate mongoTemplate;
    @Autowired
    private PromotionService promotionService;

    // @review: Priority Matrix Implementation
    // Priority 1: User-selected Promotion (Highest priority if explicitly chosen)
    // Priority 2: Admin Promotion (Auto-applied fallback)
    // Priority 3: Welcome Promotion (System-generated for first-time buyers)
    private PromotionService.AppliedPromotion resolveBestPromotion(String userId, List<String> sellerIds, BigDecimal subtotal, String selectedPromotionId) {
        // 1. Apply User Selected Promotion (Highest and only manual priority)
        if (selectedPromotionId != null && !selectedPromotionId.isEmpty()) {
            return promotionService.applyPromotion(selectedPromotionId, subtotal, userId);
        }

        // 2. Fallback to Welcome Promo ONLY if it's the first purchase
        // Admin promotions are no longer auto-applied; users must select them manually.
        if (promotionService.isFirstPurchase(userId)) {
            return promotionService.applyPromotion("WELCOME_PROMO", subtotal, userId);
        }

        return new PromotionService.AppliedPromotion(null, null, BigDecimal.ZERO);
    }

    public Order placeSingleOrder(String email, String productId, int quantity, String shippingAddress,
            String buyerName, String buyerPhone, String selectedPromotionId) {
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock");
        }

        BigDecimal subtotal = product.getPrice().multiply(new BigDecimal(quantity));
        
        // Resolve Promotion based on priority matrix
        PromotionService.AppliedPromotion applied = resolveBestPromotion(email, Collections.singletonList(product.getSellerId()), subtotal, selectedPromotionId);
        BigDecimal totalPrice = subtotal.subtract(applied.getDiscountAmount());

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
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                // Store applied promotion directly on the order (replaces user_promotion_usage)
                .appliedPromotionId(applied.getPromotionId())
                .appliedPromotionName(applied.getPromotionName())
                .discountAmount(applied.getDiscountAmount())
                .build();

        // Update stock
        product.setStockQuantity(product.getStockQuantity() - quantity);
        if (product.getStockQuantity() == 0) product.setStatus("SOLD");
        productRepository.save(product);

        order.initTracking();
        Order saved = orderRepository.save(order);

        // Increment global usage counter on the Promotion document
        promotionService.incrementPromotionUsageCounter(applied.getPromotionId());

        notifySellers(saved);
        return saved;
    }

    public Order placeOrderFromWishlist(String email, String shippingAddress, String selectedPromotionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Set<String> wishlist = user.getWishlist();
        if (wishlist == null || wishlist.isEmpty()) {
            throw new RuntimeException("Wishlist is empty");
        }

        List<Product> products = productRepository.findByIdIn(new ArrayList<>(wishlist));
        List<Order.OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        List<String> sellerIds = new ArrayList<>();
        String firstProductImage = null;

        for (Product p : products) {
            if (p.getStockQuantity() != null && p.getStockQuantity() >= 1) {
                orderItems.add(Order.OrderItem.builder()
                        .productId(p.getId())
                        .name(p.getName())
                        .price(p.getPrice())
                        .quantity(1)
                        .build());
                subtotal = subtotal.add(p.getPrice());
                if (p.getSellerId() != null) sellerIds.add(p.getSellerId());
                if (firstProductImage == null && p.getImages() != null && !p.getImages().isEmpty()) 
                    firstProductImage = p.getImages().get(0);
                
                p.setStockQuantity(p.getStockQuantity() - 1);
                if (p.getStockQuantity() == 0) p.setStatus("SOLD");
                productRepository.save(p);
            }
        }

        // Resolve Promotion
        PromotionService.AppliedPromotion applied = resolveBestPromotion(email, sellerIds, subtotal, selectedPromotionId);
        BigDecimal totalPrice = subtotal.subtract(applied.getDiscountAmount());

        Order order = Order.builder()
                .buyerId(email)
                .sellerId(sellerIds.isEmpty() ? null : sellerIds.get(0))
                .items(orderItems)
                .productName(orderItems.get(0).getName() + (orderItems.size() > 1 ? " & " + (orderItems.size()-1) + " others" : ""))
                .productImage(firstProductImage)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                // Store applied promotion directly on the order (replaces user_promotion_usage)
                .appliedPromotionId(applied.getPromotionId())
                .appliedPromotionName(applied.getPromotionName())
                .discountAmount(applied.getDiscountAmount())
                .build();

        order.initTracking();
        Order saved = orderRepository.save(order);

        // Increment global usage counter on the Promotion document
        promotionService.incrementPromotionUsageCounter(applied.getPromotionId());

        user.getWishlist().clear();
        userRepository.save(user);

        notifySellers(saved);
        return saved;
    }

    private void notifySellers(Order order) {
        if (order.getSellerId() == null) return;
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

    public List<Order> getMyOrders(String email) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(email);
    }

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    // @review: Fix for AdminController missing overloaded method
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order acceptOrder(String orderId, String sellerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        if (!order.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Unauthorized: This order does not belong to you");
        }
        
        order.setStatus("ACCEPTED");
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public Order declineOrder(String orderId, String sellerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        if (!order.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Unauthorized: This order does not belong to you");
        }
        
        order.setStatus("DECLINED");
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<Order> getOrdersForSeller(String sellerId) {
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    public Order submitReview(String orderId, String buyerId, Integer rating, String comment) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        if (!order.getBuyerId().equals(buyerId)) {
            throw new RuntimeException("Unauthorized: You did not place this order");
        }
        
        order.setRating(rating);
        order.setReview(comment);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public Order editReview(String orderId, String buyerId, Integer rating, String comment) {
        return submitReview(orderId, buyerId, rating, comment); // Same logic
    }

    public void deleteReview(String orderId, String buyerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        if (!order.getBuyerId().equals(buyerId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        order.setRating(null);
        order.setReview(null);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    public SalesReportDTO getSalesReport(String sellerId, LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .filter(o -> o.getCreatedAt().isAfter(start) && o.getCreatedAt().isBefore(end))
                .filter(o -> !"CANCELLED".equals(o.getStatus()) && !"DECLINED".equals(o.getStatus()))
                .collect(Collectors.toList());

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SalesReportDTO.OrderSummary> summaries = orders.stream()
                .map(o -> SalesReportDTO.OrderSummary.builder()
                        .orderId(o.getId())
                        .productName(o.getProductName())
                        .totalPrice(o.getTotalPrice())
                        .buyerName(o.getBuyerName())
                        .status(o.getStatus())
                        .dateCreated(o.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SalesReportDTO.builder()
                .startDate(start)
                .endDate(end)
                .totalOrders(orders.size())
                .totalRevenue(totalRevenue)
                .orders(summaries)
                .build();
    }
}
