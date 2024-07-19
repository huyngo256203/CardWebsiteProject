package com.nishikatakagi.ProductDigital.service;

import java.util.List;

import com.nishikatakagi.ProductDigital.dto.CheckoutItemDTO;
import com.nishikatakagi.ProductDigital.model.Card;
import com.nishikatakagi.ProductDigital.model.Order;
import com.nishikatakagi.ProductDigital.model.User;
import org.springframework.data.domain.Page;

public interface OrderService {
    List<Order> findOrdersByUser(User user);

    List<Card> findCardsByOrderId(int orderId);

    String doCheckout(Integer cardTypeId, Integer quantity, User user);

    List<Order> finfAllOrder();

    Page<Order> findAllOrder(Integer pageNo, Integer pageSize);

    Page<Order> findOrderPending(Integer pageNo, Integer pageSize);

    Page<Order> findOrderCompleted(Integer pageNo, Integer pageSize);

    Page<Order> findOrderReject(Integer pageNo, Integer pageSize);

    Page<Order> findOrderError(Integer pageNo, Integer pageSize);

    Page<Order> findOrderByUser(Integer pageNo, Integer pageSize, User username);
    
    void rejectOrder(int id);

    boolean isPending(int id);
    //ngoc
    Order findOrderById(int id);

    String createOrderDetail(Order order);
    //viet
    Order saveOrders(User user, List<String> publisherName,List<Integer> quantity, List<String> unitPrice);
   //huy
   Page<Order> findAllOrderlist(Integer pageNo, Integer pageSize);
}
