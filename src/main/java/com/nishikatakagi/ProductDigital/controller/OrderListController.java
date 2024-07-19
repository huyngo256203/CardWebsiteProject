package com.nishikatakagi.ProductDigital.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.lib.payos.PayOS;
import com.lib.payos.type.ItemData;
import com.lib.payos.type.PaymentData;
import com.nishikatakagi.ProductDigital.dto.UserSessionDto;
import com.nishikatakagi.ProductDigital.model.Card;
import com.nishikatakagi.ProductDigital.model.CardType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.nishikatakagi.ProductDigital.model.Order;
import com.nishikatakagi.ProductDigital.model.OrderPending;
import com.nishikatakagi.ProductDigital.model.User;
import com.nishikatakagi.ProductDigital.service.CaptchaService;
import com.nishikatakagi.ProductDigital.service.CardTypeService;
import com.nishikatakagi.ProductDigital.service.OrderPendingService;
import com.nishikatakagi.ProductDigital.service.OrderService;
import com.nishikatakagi.ProductDigital.service.UserService;

import io.jsonwebtoken.io.IOException;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequestMapping("/order")
@Controller
public class OrderListController {
    @Autowired
    private HttpSession session;
    @Autowired
    OrderService orderService;
    @Autowired
    OrderPendingService orderPendingService;
    @Autowired
    UserService userService;
    @Autowired
    CaptchaService captchaService;
    @Autowired
    CardTypeService cardTypeService;
    private final PayOS payOS;

    public OrderListController(PayOS payOS) {
        super();
        this.payOS = payOS;
    }

    Logger logger = LoggerFactory.getLogger(OrderListController.class);

    @GetMapping("")
    public String showOrderList(Model model, @RequestParam(defaultValue = "0") Integer pageNo) {
        if (pageNo < 0) {
            pageNo = 0;
        }
        int pageSize = 3; // Số lượng đơn hàng trên mỗi trang
        Page<Order> orderPage = orderService.findAllOrderlist(pageNo, pageSize);

        if (orderPage.isEmpty()) {
            model.addAttribute("error", "Không có đơn hàng nào");
        } else {
            List<Order> orders = orderPage.getContent();
            model.addAttribute("orders", orders);
        }
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("pageNo", pageNo);
        model.addAttribute("action", "/order?");
        return "publics/OrderList";
    }

    @GetMapping("/detail")
    public String showOrderDetail(Model model, @RequestParam int id, RedirectAttributes redirectAttributes) {
        Order order = orderService.findOrderById(id);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "Đơn hàng không tồn tại!");
            return "redirect:/order";
        }
        // check if the order belongs to the user
        User user = userService.findUserDBByUserSession((UserSessionDto) session.getAttribute("user_sess"));
        if (order.getUser().getId() != user.getId()) {
            redirectAttributes.addFlashAttribute("error", "Đơn hàng không tồn tại!");
            return "redirect:/order";
        }
        // check if the order is completed
        if (!order.getStatus().equals("Completed")) {
            // return to order list of view card type
            List<OrderPending> orderPendings = orderPendingService.findOrderPendingByOrderId(id);
            model.addAttribute("orderPendings", orderPendings);
            return "publics/order-pending";
        } else {
            List<Card> cards = orderService.findCardsByOrderId(id);
            model.addAttribute("cards", cards);
            return "publics/OrderDetail";
        }
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    @RequestMapping(method = RequestMethod.POST, value = "/checkout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void doCheckout(HttpServletRequest request, HttpServletResponse httpServletResponse,
                           RedirectAttributes model,
                           @RequestParam(value = "cardTypeId", required = false) Integer cardTypeId,
                           @RequestParam(value = "quantity", required = false) Integer quantity) throws java.io.IOException {
        User user = userService.findUserDBByUserSession((UserSessionDto) session.getAttribute("user_sess"));
        if (user == null) {
            if (isAjaxRequest(request)) {
                httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "ERROR_NOT_LOGGED_IN");
            } else {
                httpServletResponse.sendRedirect("/login");
            }
            return;
        }
        String result = orderService.doCheckout(cardTypeId, quantity, user);
        if (result.matches("\\d+")) { // Assuming result is the order ID if it's all digits
            try {
                // Your existing code to generate the payment link and redirect to payOS
                int random = captchaService.createIDCaptcha();
                session.setAttribute("successID", random);
                String description = "Thanh toan don hang";
                String returnUrl = "http://localhost:8080/success?orderid=" + result + "&rd=" + random;
                String cancelUrl = "http://localhost:8080/";
                String currentTimeString = String.valueOf(new Date().getTime());
                int orderCode = Integer.parseInt(currentTimeString.substring(currentTimeString.length() - 6));
                // Duyệt từng đơn hàng 1 và cho vào ItemList
                List<ItemData> itemList = new ArrayList<>();
                int total = 0;
                CardType cardType = cardTypeService.findById(cardTypeId);
                int price = (int) cardType.getUnitPrice();
                ItemData item = new ItemData(cardType.getPublisher().getName(), quantity, price);
                itemList.add(item);
                total += quantity * price;
                PaymentData paymentData = new PaymentData(orderCode, total, description,
                        itemList, cancelUrl, returnUrl);

                JsonNode data = payOS.createPaymentLink(paymentData);
                if (data.has("checkoutUrl")) {
                    int paymentId = data.get("orderCode").asInt();
                    System.out.println("Payment orderCode: " + paymentId);
                    session.setAttribute("orderCodeTranstaction", paymentId);

                    String checkoutUrl = data.get("checkoutUrl").asText();
                    httpServletResponse.setHeader("Location", checkoutUrl);
                    httpServletResponse.setStatus(302);
                } else {
                    System.out.println("Checkout URL not found in API response");
                    model.addFlashAttribute("error", "Payment initiation failed");
                    httpServletResponse.sendRedirect("/");
                }
            } catch (Exception e) {
                System.out.println("Error during payment: " + e.getMessage());
                model.addFlashAttribute("error", "Internal server error during payment");
                httpServletResponse.sendRedirect("/");
            }
        } else {
            // Handle case where order creation failed
            System.out.println("Order creation failed: " + result);
            model.addFlashAttribute("error", result);
            httpServletResponse.sendRedirect("/");
        }
    }
}
