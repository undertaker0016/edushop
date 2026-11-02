package com.edushop.service;

import com.edushop.dto.payments.PaymentResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private final JdbcTemplate jdbc;
  private final PaymentGateway gateway;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PaymentService(
      JdbcTemplate jdbc, PaymentGateway gateway, Clock clock, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.gateway = gateway;
    this.clock = clock;
    this.tx = new TransactionTemplate(manager);
  }

  public PaymentResponse initiate(long userId, boolean admin, long orderId, String key) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{8,79}"))
      throw error(400, "Invalid Idempotency-Key");
    String dbKey = orderId + ":" + key;
    long id =
        Objects.requireNonNull(
            tx.execute(
                s -> {
                  var o = order(orderId, true);
                  if (!admin && number(o, "user_id") != userId) throw error(404, "Order not found");
                  var prior =
                      jdbc.queryForList(
                          "SELECT id FROM payments WHERE order_id=? AND idempotency_key=?",
                          orderId,
                          dbKey);
                  if (!prior.isEmpty()) return number(prior.getFirst(), "id");
                  if (!"PENDING".equals(o.get("status")) || expired(o))
                    throw error(409, "Order is not awaiting payment");
                  if (((BigDecimal) o.get("total_amount")).signum() == 0)
                    throw error(409, "Zero-total orders do not require payment");
                  if (jdbc.queryForObject(
                          "SELECT COUNT(*) FROM payments WHERE order_id=? AND status='PENDING'",
                          Long.class,
                          orderId)
                      > 0)
                    throw error(
                        409,
                        "A pending payment exists; retry with its original key or reconcile it");
                  if (gateway.providerName().equals("UNCONFIGURED"))
                    throw error(503, "Payment provider is not configured");
                  String ref = gateway.reference(dbKey, clock.instant());
                  jdbc.update(
                      "INSERT INTO"
                          + " payments(order_id,provider,idempotency_key,provider_order_ref,amount,currency,status)"
                          + " VALUES(?,?,?,?,?,?,'PENDING')",
                      orderId,
                      gateway.providerName(),
                      dbKey,
                      ref,
                      o.get("total_amount"),
                      o.get("currency"));
                  return jdbc.queryForObject(
                      "SELECT id FROM payments WHERE idempotency_key=?", Long.class, dbKey);
                }));
    var p = payment(id);
    if (!"PENDING".equals(p.get("status")) || p.get("checkout_url") != null) return response(p);
    if (!gateway.providerName().equals(p.get("provider")))
      throw error(
          409,
          "Giao dịch thuộc cổng thanh toán trước đó. Cần khôi phục cấu hình cổng cũ để kiểm tra,"
              + " không tạo thanh toán lặp ở cổng mới.");
    try {
      var session =
          gateway.initiate(
              (String) p.get("provider_order_ref"),
              (BigDecimal) p.get("amount"),
              (String) p.get("currency"),
              (String) p.get("provider_order_ref"));
      tx.executeWithoutResult(
          s -> {
            order(orderId, true);
            jdbc.update(
                "UPDATE payments SET"
                    + " checkout_url=?,provider_transaction_id=COALESCE(provider_transaction_id,?),result_note=NULL"
                    + " WHERE id=? AND status='PENDING'",
                session.checkoutUrl(),
                session.transactionId(),
                id);
          });
    } catch (RuntimeException ex) {
      // The provider may have accepted a timed-out request. Keep its durable reference for
      // query/retry.
      tx.executeWithoutResult(
          s ->
              jdbc.update(
                  "UPDATE payments SET result_note='Initiation uncertain: retry same key or"
                      + " reconcile' WHERE id=? AND status='PENDING'",
                  id));
      throw ex;
    }
    return response(payment(id));
  }

  public List<PaymentResponse> listForOrder(long userId, boolean admin, long orderId) {
    var o = order(orderId, false);
    if (!admin && number(o, "user_id") != userId) throw error(404, "Order not found");
    return jdbc
        .queryForList(
            "SELECT"
                + " id,order_id,provider,status,amount,currency,provider_transaction_id,checkout_url,paid_at"
                + " FROM payments WHERE order_id=? ORDER BY id",
            orderId)
        .stream()
        .map(this::response)
        .toList();
  }

  public PaymentResponse resume(long userId, long paymentId) {
    var p = payment(paymentId);
    long orderId = number(p, "order_id");
    if (number(order(orderId, false), "user_id") != userId)
      throw error(404, "Không tìm thấy giao dịch.");
    String prefix = orderId + ":";
    String stored = (String) p.get("idempotency_key");
    if (stored == null || !stored.startsWith(prefix))
      throw error(409, "Giao dịch này cần quản trị viên kiểm tra.");
    return initiate(userId, false, orderId, stored.substring(prefix.length()));
  }

  public java.util.Map<String, Object> resolve(long userId, String reference) {
    if (reference == null || !reference.matches("(?:PAY-[0-9a-fA-F-]{36}|[0-9]{6}_[0-9a-f]{32})"))
      throw error(400, "Mã tham chiếu thanh toán không hợp lệ.");
    var rows =
        jdbc.queryForList(
            "SELECT p.id AS paymentId,p.provider AS provider,p.order_id AS orderId,p.status AS"
                + " paymentStatus,o.status AS orderStatus,p.reconciliation_required AS"
                + " reconciliationRequired FROM payments p JOIN orders o ON o.id=p.order_id WHERE"
                + " p.provider_order_ref=? AND o.user_id=?",
            reference,
            userId);
    if (rows.size() != 1) throw error(404, "Không tìm thấy giao dịch thuộc tài khoản này.");
    return rows.getFirst();
  }

  public java.util.Map<String, Object> configuration() {
    String provider = gateway.providerName();
    return java.util.Map.of(
        "provider",
        provider,
        "online",
        provider.startsWith("MOMO_") || provider.equals("ZALOPAY_SANDBOX"),
        "sandbox",
        provider.endsWith("_SANDBOX"),
        "publicDemo",
        gateway.publicDemo());
  }

  /** Only provider adapters that have authenticated a result may invoke this method. */
  public void applyVerifiedResult(
      String provider,
      String transactionId,
      long orderId,
      BigDecimal amount,
      String currency,
      boolean succeeded) {
    tx.executeWithoutResult(
        s -> {
          var o = order(orderId, true);
          var rows =
              jdbc.queryForList(
                  "SELECT id FROM payments WHERE order_id=? AND provider=? AND"
                      + " provider_transaction_id=?",
                  orderId,
                  provider,
                  transactionId);
          if (rows.size() != 1) throw error(404, "Payment attempt not found");
          apply(o, payment(number(rows.getFirst(), "id")), amount, currency, succeeded);
        });
  }

  /** MoMo references are persisted BEFORE the network call; an early IPN can find them. */
  public void applyProviderReference(
      String provider,
      String ref,
      String requestId,
      String transactionId,
      BigDecimal amount,
      boolean succeeded) {
    if (!ref.equals(requestId)) throw error(409, "Payment request reference mismatch");
    var rows =
        jdbc.queryForList(
            "SELECT id,order_id FROM payments WHERE provider=? AND provider_order_ref=?",
            provider,
            ref);
    if (rows.size() != 1) throw error(404, "Payment reference not found");
    long id = number(rows.getFirst(), "id"), orderId = number(rows.getFirst(), "order_id");
    tx.executeWithoutResult(
        s -> {
          var o = order(orderId, true);
          var p = payment(id);
          if (((BigDecimal) p.get("amount")).compareTo(amount) != 0)
            throw error(409, "Payment amount mismatch");
          String old = (String) p.get("provider_transaction_id");
          // A cancelled MoMo attempt may have transId=0: it is NOT a unique transaction ID.
          boolean hasTransaction =
              transactionId != null
                  && transactionId.matches("[0-9]+")
                  && new java.math.BigInteger(transactionId).signum() > 0;
          if (succeeded && !hasTransaction) throw error(400, "Thiếu mã giao dịch MoMo thành công.");
          if (hasTransaction) {
            if (old != null && !old.equals(transactionId))
              throw error(409, "Provider transaction mismatch");
            jdbc.update(
                "UPDATE payments SET provider_transaction_id=? WHERE id=?", transactionId, id);
          }
          apply(o, p, amount, "VND", succeeded);
        });
  }

  private void apply(
      Map<String, Object> o,
      Map<String, Object> p,
      BigDecimal amount,
      String currency,
      boolean succeeded) {
    long id = number(p, "id"), orderId = number(o, "id");
    if (((BigDecimal) p.get("amount")).compareTo(amount) != 0
        || !currency.equals(p.get("currency"))
        || ((BigDecimal) o.get("total_amount")).compareTo(amount) != 0
        || !currency.equals(o.get("currency")))
      throw error(409, "Payment amount or currency mismatch");
    if ("SUCCEEDED".equals(p.get("status"))) {
      if (succeeded) return;
      throw error(409, "Payment already succeeded");
    }
    if ("FAILED".equals(p.get("status"))) {
      if (!succeeded) return;
      throw error(409, "Conflicting provider result requires reconciliation");
    }
    if (!succeeded) {
      jdbc.update(
          "UPDATE payments SET status='FAILED',result_note='Provider reported failure' WHERE id=?"
              + " AND status='PENDING'",
          id);
      return; // Buyer can retry while the original 15-minute reservation remains valid.
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (!"PENDING".equals(o.get("status")) || expired(o)) {
      jdbc.update(
          "UPDATE payments SET"
              + " status='SUCCEEDED',paid_at=?,reconciliation_required=TRUE,result_note='Paid after"
              + " reservation ended: manual reconciliation required' WHERE id=?",
          now,
          id);
      if ("PENDING".equals(o.get("status"))) {
        jdbc.update("UPDATE orders SET status='EXPIRED' WHERE id=?", orderId);
        releaseCoupon(orderId);
      }
      jdbc.update(
          "INSERT INTO notifications(user_id,title,body,type,action_url) VALUES(?,'Cần đối soát thanh"
              + " toán','Thanh toán đến sau khi giữ chỗ kết thúc. Vui lòng liên hệ quản trị. Chưa"
              + " cấp quyền lớp học.','PAYMENT_RECONCILIATION','/buyer/orders')",
          o.get("user_id"));
      return;
    }
    jdbc.update(
        "UPDATE payments SET status='SUCCEEDED',paid_at=?,result_note=NULL WHERE id=?", now, id);
    jdbc.update(
        "UPDATE orders SET status='PAID',paid_at=?,expires_at=NULL WHERE id=?", now, orderId);
    jdbc.update(
        "UPDATE coupon_redemptions SET status='CONSUMED' WHERE order_id=? AND status='RESERVED'",
        orderId);
    jdbc.update(
        """
INSERT INTO enrollments(user_id,offering_id,order_item_id,status)
SELECT o.user_id,oi.offering_id,oi.id,'ACTIVE' FROM orders o JOIN order_items oi ON oi.order_id=o.id
WHERE o.id=? AND NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.order_item_id=oi.id)
""",
        orderId);
    jdbc.update(
        "INSERT INTO notifications(user_id,title,body,type,action_url) VALUES(?,'Thanh toán thành công','Đơn"
            + " hàng đã thanh toán và ghi danh.','ORDER_PAID',?)",
        o.get("user_id"),"/buyer/classroom");
  }

  public Map<String, Object> payment(long id) {
    var rows =
        jdbc.queryForList(
            "SELECT"
                + " id,order_id,provider,provider_order_ref,idempotency_key,provider_transaction_id,amount,currency,status,checkout_url,paid_at"
                + " FROM payments WHERE id=?",
            id);
    if (rows.isEmpty()) throw error(404, "Payment not found");
    return rows.getFirst();
  }

  private Map<String, Object> order(long id, boolean lock) {
    var rows =
        jdbc.queryForList(
            "SELECT id,user_id,status,total_amount,currency,expires_at FROM orders WHERE id=?"
                + (lock ? " FOR UPDATE" : ""),
            id);
    if (rows.isEmpty()) throw error(404, "Order not found");
    return rows.getFirst();
  }

  private boolean expired(Map<String, Object> o) {
    var value = o.get("expires_at");
    return value == null || !local(value).isAfter(LocalDateTime.now(clock));
  }

  private void releaseCoupon(long orderId) {
    var rows =
        jdbc.queryForList(
            "SELECT coupon_id FROM coupon_redemptions WHERE order_id=? AND status='RESERVED' FOR"
                + " UPDATE",
            orderId);
    if (jdbc.update(
            "UPDATE coupon_redemptions SET status='RELEASED' WHERE order_id=? AND"
                + " status='RESERVED'",
            orderId)
        > 0)
      for (var r : rows)
        jdbc.update(
            "UPDATE coupons SET used_count=GREATEST(used_count-1,0) WHERE id=?",
            r.get("coupon_id"));
  }

  private PaymentResponse response(Map<String, Object> p) {
    return new PaymentResponse(
        number(p, "id"),
        number(p, "order_id"),
        (String) p.get("provider"),
        (String) p.get("status"),
        (BigDecimal) p.get("amount"),
        (String) p.get("currency"),
        (String) p.get("provider_transaction_id"),
        (String) p.get("checkout_url"),
        p.get("paid_at") == null ? null : local(p.get("paid_at")));
  }

  private static LocalDateTime local(Object o) {
    return o instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) o;
  }

  private static long number(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).longValue();
  }

  private static ResponseStatusException error(int status, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(status), message);
  }
}
