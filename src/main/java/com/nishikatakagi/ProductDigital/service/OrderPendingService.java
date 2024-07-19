package com.nishikatakagi.ProductDigital.service;

import java.util.List;

import com.nishikatakagi.ProductDigital.model.CardType;
import com.nishikatakagi.ProductDigital.model.Order;
import com.nishikatakagi.ProductDigital.model.OrderPending;
import com.nishikatakagi.ProductDigital.model.User;

public interface OrderPendingService {

    List<OrderPending> findOrderPendingByOrderId(int id);

    String addOrderPending(Integer cardTypeId, Integer quantity, User user);

    void saveOrderPending(CardType cardType, Integer quantity, Order order);
} 
