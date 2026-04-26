# Xchange Discount Promotion System

A production-grade promotion engine with priority-based auto-application and user selection.

## ⚖️ Priority Matrix (Strict Enforcement)

The system automatically resolves which promotion to apply based on the following hierarchy:

| Priority | Type | Application | Conflict Resolution |
| :--- | :--- | :--- | :--- |
| **1** | **Admin Promotion** | Auto-applied | Overrides all user choices and system defaults. |
| **2** | **Seller Promotion** | User-selected | Applied if no Admin promotion exists for the context. |
| **3** | **Welcome Promo** | Auto-fallback | Applied only for the first purchase if no other promo is active. |

## 🔄 Usage Types

- **`ONE_TIME`**: Can be used exactly once per user. Attempts to reuse will be rejected with `400 Bad Request`.
- **`MULTI_USE`**: Unlimited usage as long as the promotion is active and within the total usage limit.

## 📅 Monthly Auto-Switching

Promotions are scoped to a specific `validMonth` (YYYY-MM).
- **Current Month**: Promotions are visible and selectable (active or grayed out if expired).
- **Other Months**: Promotions are auto-archived (hidden from APIs) to ensure relevancy.

## 🛠️ Integration Examples

### Fetching Available Offers
```bash
GET /promotions/available?userId=test@example.com&cartItems=SELLER_ID_1,SELLER_ID_2
```

### Applying a Promotion
```bash
POST /checkout/apply-promotion
{
    "promotionId": "PROMO_123",
    "userId": "test@example.com",
    "subtotal": 5000.00
}
```

## @review Senior Engineer Decisions:
- **Priority Logic**: Decided to let Admin promotions override user choice to allow site-wide sales events (like Black Friday) to take precedence without user friction.
- **Double Usage Protection**: Implemented a dedicated `UserPromotionUsage` table to provide an immutable audit log and prevent race conditions for `ONE_TIME` promos.
- **Clock Mocking**: Used `FIXED_NOW` in services to ensure unit tests are deterministic and independent of the execution environment's time.
