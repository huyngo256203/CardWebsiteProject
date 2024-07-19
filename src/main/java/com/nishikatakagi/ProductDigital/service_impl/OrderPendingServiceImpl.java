package com.nishikatakagi.ProductDigital.service_impl;

import java.util.List;

import com.nishikatakagi.ProductDigital.model.CardType;
import com.nishikatakagi.ProductDigital.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nishikatakagi.ProductDigital.model.OrderPending;
import com.nishikatakagi.ProductDigital.model.User;
import com.nishikatakagi.ProductDigital.repository.OrderPendingRepository;
import com.nishikatakagi.ProductDigital.service.OrderPendingService;

@Service
public class OrderPendingServiceImpl implements OrderPendingService{

    @Autowired
    OrderPendingRepository orderPendingRepository;

    @Override
    public List<OrderPending> findOrderPendingByOrderId(int orderId) {
        return orderPendingRepository.findOrderPendingByOrderId(orderId);
    }

    @Override
    public String addOrderPending(Integer cardTypeId, Integer quantity, User user) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addOrderPending'");
    }

    @Override
    public void saveOrderPending(CardType cardType, Integer quantity, Order order) {
        OrderPending orderPending = new OrderPending();
        orderPending.setOrder(order);
        orderPending.setQuantity(quantity);
        orderPending.setCardType(cardType);

        orderPendingRepository.save(orderPending);

    }

}
