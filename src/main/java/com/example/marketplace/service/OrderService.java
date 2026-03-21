package com.example.marketplace.service;

import com.example.marketplace.model.Order;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.User;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;

    public Order placeOrderFromWishlist(String email, String shippingAddress) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Set<String> wishlist = user.getWishlist();
        if (wishlist == null || wishlist.isEmpty()) {
            throw new RuntimeException("Wishlist is empty");
        }

        List<Order.OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (String productId : wishlist) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
            
            orderItems.add(Order.OrderItem.builder()
                    .productId(productId)
                    .name(product.getName())
                    .price(product.getPrice())
                    .quantity(1)
                    .build());
            
            totalPrice = totalPrice.add(product.getPrice());
        }

        Order order = Order.builder()
                .buyerId(email)
                .items(orderItems)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Clear wishlist after order
        user.getWishlist().clear();
        userRepository.save(user);

        return orderRepository.save(order);
    }

    public List<Order> getMyOrders(String email) {
        return orderRepository.findByBuyerId(email);
    }
}
