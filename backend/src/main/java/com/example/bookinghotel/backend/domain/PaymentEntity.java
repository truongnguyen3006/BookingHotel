package com.example.bookinghotel.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_booking_idempotency", columnNames = {"booking_id", "idempotency_key"})
})
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "provider_reference", length = 100, unique = true)
    private String providerReference;

    @Column(name = "provider_response_code", length = 20)
    private String providerResponseCode;

    @Column(name = "provider_transaction_status", length = 20)
    private String providerTransactionStatus;

    @Column(name = "amount_vnd")
    private Long amountVnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentEntity() {}

    public PaymentEntity(BookingEntity booking, PaymentMethod method, PaymentStatus status,
                         String transactionId, String idempotencyKey, Instant paidAt, Instant createdAt) {
        this(booking, method, status, transactionId, idempotencyKey, null, null, null, null, paidAt, createdAt);
    }

    public PaymentEntity(BookingEntity booking, PaymentMethod method, PaymentStatus status,
                         String transactionId, String idempotencyKey, String providerReference,
                         String providerResponseCode, String providerTransactionStatus, Long amountVnd,
                         Instant paidAt, Instant createdAt) {
        this.booking = booking;
        this.method = method;
        this.status = status;
        this.transactionId = transactionId;
        this.idempotencyKey = idempotencyKey;
        this.providerReference = providerReference;
        this.providerResponseCode = providerResponseCode;
        this.providerTransactionStatus = providerTransactionStatus;
        this.amountVnd = amountVnd;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public BookingEntity getBooking() { return booking; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProviderReference() { return providerReference; }
    public String getProviderResponseCode() { return providerResponseCode; }
    public String getProviderTransactionStatus() { return providerTransactionStatus; }
    public Long getAmountVnd() { return amountVnd; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void completeProviderPayment(
            PaymentStatus status,
            String transactionId,
            String responseCode,
            String transactionStatus,
            Instant paidAt
    ) {
        this.status = status;
        this.transactionId = transactionId;
        this.providerResponseCode = responseCode;
        this.providerTransactionStatus = transactionStatus;
        this.paidAt = paidAt;
    }
}
