package com.nishikatakagi.ProductDigital.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nishikatakagi.ProductDigital.model.OrderPending;

@Repository
public interface OrderPendingRepository extends JpaRepository<OrderPending, Integer>{

    List<OrderPending> findOrderPendingByOrderId(int orderId);

} 
