package com.nishikatakagi.ProductDigital.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "order_pendings")
public class OrderPending {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;
    @ManyToOne
    @JoinColumn(name = "order_id")
    Order order;
    @ManyToOne
    @JoinColumn(name = "card_type_id")
    CardType cardType;
    int quantity;
    @Column(columnDefinition = "decimal(10,2)")
    double total;

    // calculate cost for a specific item in cart
    public double getTotal() {
        total = cardType.getUnitPrice() * quantity;
        return total;
    }
}
