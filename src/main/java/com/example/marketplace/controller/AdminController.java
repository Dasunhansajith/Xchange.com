package com.example.marketplace.controller;

import com.example.marketplace.model.SellerApplication;
import com.example.marketplace.model.Order;
import com.example.marketplace.model.Shop;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.OrderTracking;
import com.example.marketplace.model.User;
import com.example.marketplace.service.SellerService;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.UserService;
import com.example.marketplace.service.NotificationService;
import com.example.marketplace.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.example.marketplace.repository.ShopRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.OrderTrackingRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired
    private SellerService sellerService;
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderTrackingRepository orderTrackingRepository;

    @Autowired
    private com.example.marketplace.service.NotificationService notificationService;

    @GetMapping("/hello")
    public String hello() {
        return "Hello Admin!";
    }

    @GetMapping("/seller-applications/pending")
    public ResponseEntity<List<SellerApplication>> getPendingSellerApplications() {
        return ResponseEntity.ok(sellerService.getPendingApplications());
    }

    @GetMapping("/seller-applications")
    public ResponseEntity<List<SellerApplication>> getAllSellerApplications() {
        return ResponseEntity.ok(sellerService.getApplications());
    }

    @PostMapping("/seller-applications/{applicationId}/approve")
    public ResponseEntity<SellerApplication> approveSellerApplication(
            @PathVariable String applicationId,
            Authentication auth) {
        SellerApplication approved = sellerService.approveApplication(applicationId, auth.getName());
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/seller-applications/{applicationId}/reject")
    public ResponseEntity<SellerApplication> rejectSellerApplication(
            @PathVariable String applicationId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        String rejectionReason = request.getOrDefault("reason", "Your application does not meet our requirements");
        SellerApplication rejected = sellerService.rejectApplication(applicationId, auth.getName(), rejectionReason);
        return ResponseEntity.ok(rejected);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getAllOrders(org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable).getContent());
    }

    @PostMapping("/users/{userId}/role")
    public ResponseEntity<UserDto> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        if (role == null || role.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UserDto updatedUser = userService.updateUserRole(userId, role);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/users/{userId}/role")
    public ResponseEntity<UserDto> removeUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        if (role == null || role.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UserDto updatedUser = userService.removeUserRole(userId, role);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shops")
    public ResponseEntity<List<Shop>> getAllShops() {
        List<Shop> shops = shopRepository.findAll();
        return ResponseEntity.ok(shops);
    }

    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<Void> deleteShop(@PathVariable String shopId) {
        // Get the shop to find the userId
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
        
        String userId = shop.getUserId();
        
        // Get the user to find the email (sellerId in orders)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String sellerEmail = user.getEmail();
        String shopName = shop.getShopName();
        
        // Get all orders associated with this seller
        List<Order> orders = orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerEmail);
        
        // Send notifications to buyers with pending orders and update order status
        for (Order order : orders) {
            // Check if order is not completed/cancelled
            if (!order.getStatus().equals("COMPLETED") && !order.getStatus().equals("CANCELLED")) {
                // Get the buyer's email to send notification
                String buyerEmail = order.getBuyerId();
                
                // Send notification to buyer
                notificationService.createNotification(
                        buyerEmail,
                        "Order Cancelled - Store Removed ❌",
                        "Your order from \"" + shopName + "\" cannot be fulfilled as the store has been removed from the platform. " +
                        "Please contact our admin team for a refund. We apologize for the inconvenience. " +
                        "Order ID: " + order.getId(),
                        "ORDER_CANCELLED_STORE_REMOVED",
                        order.getId()
                );
                
                // Update order status to CANCELLED
                order.setStatus("CANCELLED");
                order.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(order);
            }
        }
        
        // Delete all orders associated with this seller (after notifications sent)
        for (Order order : orders) {
            orderRepository.delete(order);
        }
        
        // Delete all order tracking records for this seller
        List<OrderTracking> trackingRecords = orderTrackingRepository.findBySellerIdOrderByCreatedAtDesc(sellerEmail);
        for (OrderTracking tracking : trackingRecords) {
            orderTrackingRepository.delete(tracking);
        }
        
        // Delete all products associated with this shop
        List<Product> products = productRepository.findByShopId(shopId);
        for (Product product : products) {
            productRepository.delete(product);
        }
        
        // Clear the user's shopId
        if (userId != null) {
            userRepository.findById(userId).ifPresent(userToUpdate -> {
                userToUpdate.setShopId(null);
                userRepository.save(userToUpdate);
            });
        }
        
        // Delete the shop
        shopRepository.deleteById(shopId);
        
        return ResponseEntity.noContent().build();
    }
}
