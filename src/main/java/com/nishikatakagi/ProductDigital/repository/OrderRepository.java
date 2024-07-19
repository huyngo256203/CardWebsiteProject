package com.nishikatakagi.ProductDigital.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nishikatakagi.ProductDigital.model.Order;
import com.nishikatakagi.ProductDigital.model.User;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUser(User user);

    Page<Order> findByStatus(String status, Pageable pageable);

    Page<Order> findByUser(User user, Pageable pageable);

    Page<Order> findAll(Pageable pageable);
}
