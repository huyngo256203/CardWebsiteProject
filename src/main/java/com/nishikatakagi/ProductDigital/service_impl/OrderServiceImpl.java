package com.nishikatakagi.ProductDigital.service_impl;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.nishikatakagi.ProductDigital.dto.CheckoutItemDTO;
import com.nishikatakagi.ProductDigital.model.*;
import com.nishikatakagi.ProductDigital.repository.*;
import com.nishikatakagi.ProductDigital.service.CardTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.nishikatakagi.ProductDigital.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private CardTypeRepository cardTypeRepository;
    @Autowired
    CardTypeService cardTypeService;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    OrderPendingRepository orderPendingRepository;
    private final Object lock = new Object();

    @Override
    public List<Order> findOrdersByUser(User user) {
        return orderRepository.findByUser(user);
    }

    @Override
    public List<Card> findCardsByOrderId(int orderId) {
        // Fetch all OrderDetails for the given orderId
        List<OrderDetail> orderDetails = orderDetailRepository.findByOrderId(orderId);

        // Map OrderDetails to Cards and collect them in a list
        return orderDetails.stream()
                .map(OrderDetail::getCard)
                .collect(Collectors.toList());
    }

    @Override
    public String doCheckout(Integer cardTypeId, Integer quantity, User user) {
        if (cardTypeId == null || quantity == null) {
            return "Vui lòng chọn sản phẩm và số lượng!";
        }

        Optional<CardType> cardTypeOpt = cardTypeRepository.findById(cardTypeId);
        if (!cardTypeOpt.isPresent()) {
            return "Loại thẻ không tồn tại!";
        }

        CardType cardType = cardTypeOpt.get();

        if (quantity <= 0 || quantity > cardType.getInStock()) {
            return "Số lượng không hợp lệ!";
        }

        List<Card> availableCards = cardRepository.findByCardTypeAndIsDeletedOrderByExpiryDateAsc(cardType, false);
        if (availableCards.size() < quantity) {
            return "Không đủ thẻ để thanh toán!";
        }
        // // Tạo đơn hàng mới
        Order order = new Order();
        order.setUser(user);
        order.setTotalMoney(cardType.getUnitPrice() * quantity);
        order.setStatus("Pending");
        order.setOrderDate(new Date(System.currentTimeMillis()));
        orderRepository.save(order);

        // Save to order pending
        OrderPending orderPending = new OrderPending();
        orderPending.setOrder(order);
        orderPending.setCardType(cardType);
        orderPending.setQuantity(quantity);
        orderPendingRepository.save(orderPending);
        return String.valueOf(order.getId());
    }
    
    @Override
    public List<Order> finfAllOrder() {
        return orderRepository.findAll();
    }

    @Override
    public Page<Order> findAllOrder(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findAll(pageable);
    }

    @Override
    public Page<Order> findOrderPending(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findByStatus("Pending", pageable);
    }

    @Override
    public Page<Order> findOrderCompleted(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findByStatus("Completed", pageable);
    }

    @Override
    public Page<Order> findOrderReject(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findByStatus("Reject", pageable);
    }

    @Override
    public Page<Order> findOrderByUser(Integer pageNo, Integer pageSize, User user) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findByUser(user, pageable);
    }

    @Override
    public void rejectOrder(int id) {
        Order order = orderRepository.findById(id).get();
        order.setStatus("Reject");
        order.setNote("Đơn hàng đã bị hủy bởi quản trị viên");
        orderRepository.save(order);
    }

    @Override
    public boolean isPending(int id) {
        Order order = orderRepository.findById(id).get();
        return order.getStatus().equals("Pending");
    }

    @Override
    public Order findOrderById(int id) {
        return orderRepository.findById(id).orElse(null);
    }

    // checkout doi vs 1 hoac nhieu loai san pham
    @Override
    public String createOrderDetail(Order order) {
        synchronized (lock) {
            // create card from order pending
            // get order pending by order id
            // get card type by card type id
            // get quantity by order pending
            // get user by order id
            // create card
            // set card type, user, expiry date, is deleted, deleted date, deleted by
            // save card
            // delete order pending
            // return to order detail
            List<OrderPending> orderPendings = orderPendingRepository.findOrderPendingByOrderId(order.getId());
            for (OrderPending orderPending : orderPendings) {
                CardType cardType = orderPending.getCardType();
                Integer quantity = orderPending.getQuantity();
                List<Card> availableCards = cardRepository.findByCardTypeAndIsDeletedOrderByExpiryDateAsc(cardType,
                        false);
                // check if the quantity of card is enough
                if (availableCards.size() < quantity) {
                    order.setStatus("Error");
                    order.setNote("Không đủ thẻ nạp để thanh toán!");
                    orderRepository.save(order);
                    return "Không đủ thẻ nạp để thanh toán!";
                }
            }
            for (OrderPending orderPending : orderPendings) {
                CardType cardType = orderPending.getCardType();
                Integer quantity = orderPending.getQuantity();
                List<Card> availableCards = cardRepository.findByCardTypeAndIsDeletedOrderByExpiryDateAsc(cardType,
                        false);
                List<Card> cardsToCheckout = availableCards.subList(0, quantity);
                for (Card card : cardsToCheckout) {
                    card.setIsDeleted(true);
                    card.setDeletedDate(new Date(System.currentTimeMillis()));
                    card.setDeletedBy(order.getUser().getId());
                    cardRepository.save(card);
                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setOrder(order);
                    orderDetail.setCard(card);
                    orderDetailRepository.save(orderDetail);
                }
                cardType.setInStock(cardType.getInStock() - quantity);
                cardType.setSoldQuantity(cardType.getSoldQuantity() + quantity);
                cardTypeRepository.save(cardType);
            }
            order.setStatus("Completed");
            orderRepository.save(order);
            return "Đơn hàng đã hoàn thành!";
        }
    }

    @Override
    public Order saveOrders(User user, List<String> publisherName, List<Integer> quantity, List<String> unitPrice) {
        Order order = new Order();
        order.setUser(user);
        java.util.Date date = new java.util.Date();
        order.setOrderDate(date);

        double totalMoney = 0;
        for(int i = 0; i < publisherName.size(); i++){
            int price = Integer.parseInt(unitPrice.get(i));
            totalMoney += quantity.get(i) * price;
        }

        order.setTotalMoney(totalMoney);
        order.setStatus("Pending");
        orderRepository.save(order);

        return order;
    }

    @Override
    public Page<Order> findAllOrderlist(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findAll(pageable);
    }

    @Override
    public Page<Order> findOrderError(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return orderRepository.findByStatus("Error", pageable);
    }
}
