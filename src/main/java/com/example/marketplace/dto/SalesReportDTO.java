package com.example.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDTO {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int totalOrders;
    private BigDecimal totalRevenue;
    private List<OrderSummary> orders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private String orderId;
        private String productName;
        private int quantity;
        private BigDecimal price;
        private BigDecimal totalPrice;
        private String buyerName;
        private String status;
        private LocalDateTime dateCreated;
    }
}
