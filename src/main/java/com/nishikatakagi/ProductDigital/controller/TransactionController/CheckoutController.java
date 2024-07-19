package com.nishikatakagi.ProductDigital.controller.TransactionController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lib.payos.PayOS;
import com.lib.payos.type.ItemData;
import com.lib.payos.type.PaymentData;
import com.nishikatakagi.ProductDigital.dto.UserSessionDto;
import com.nishikatakagi.ProductDigital.model.*;
import com.nishikatakagi.ProductDigital.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
public class CheckoutController {
    private final PayOS payOS;

    @Autowired
    OrderService orderService;

    @Autowired
    CardService cardService;

    @Autowired
    CaptchaService captchaService;

    @Autowired
    OrderPendingService orderPendingService;

    @Autowired
    CardTypeService cardTypeService;

    @Autowired
    UserService userService;

    @Autowired
    HttpSession session;

    public CheckoutController(PayOS payOS) {
        super();
        this.payOS = payOS;
    }

    @RequestMapping(value = "/success")
    public String Success(Model model, @RequestParam(value = "orderid", required = false) Integer orderid, @RequestParam(value = "rd", required = false) Integer rd) {
        // Add your validation logic here
        if(orderid == null || rd == null || orderid <= 0 || rd <= 0) {
            return "redirect:/";
        }
        int rdSession = (Integer) session.getAttribute("successID");
        if(rdSession != rd){
            return "redirect:/cart";
        }else{
            // Lấy danh sách hàng cần thanh toán
            List<OrderPending> listOrderPending = orderPendingService.findOrderPendingByOrderId(orderid);
            String error = "";

            // Kiểm tra số lượng
            for (OrderPending orderPending : listOrderPending) {
                if(orderPending.getCardType().getInStock() < orderPending.getQuantity()){
                    error += orderPending.getCardType().getPublisher().getName() + " mệnh giá " + orderPending.getCardType().getUnitPrice() + "VND ";
                }
            }
            if(!error.isEmpty()){
                session.setAttribute("errorNotStock", "Xin lỗi quý khách " + error +" hiện đang không đủ số lượng trong kho");
                return "redirect:/cart";
            }else {
                Order order = orderService.findOrderById(orderid);
                String status = orderService.createOrderDetail(order);
                session.removeAttribute("successID");
                if(status.equals("Đơn hàng đã hoàn thành!")){
                    // nếu các thủ tục đã oke thì gọi qua paymen-info để lấy được thông tin giao dịch, lưu vào database
                    int orderCode = (Integer) session.getAttribute("orderCodeTranstaction");
                    session.removeAttribute("orderCodeTranstaction");
                    // Tạo session orderID để sau khi tạo được thông tin giao dịch sẽ chuyến đến trang order detail với id mong muốn
                    session.setAttribute("orderId",orderid);
                    return "redirect:/payment-info/" + orderCode;
                }
            }

        }
        return "redirect:/cart";
    }

    @RequestMapping(value = "/cancel")
    public String Cancel() {
        return "cancel";
    }

    @RequestMapping(method = RequestMethod.POST, value = "/create-payment-link", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void checkout(Model model, HttpServletResponse httpServletResponse, @RequestParam List<String> publisherName, @RequestParam List<Integer> quantity, @RequestParam List<String> unitPrice) {
        ObjectMapper objectMapper = new ObjectMapper();
        UserSessionDto userSessionDto = (UserSessionDto) session.getAttribute("user_sess");
        if (userSessionDto == null)
            returnLogin();
        try {
            int sizeCart = sizeCart(publisherName, quantity, unitPrice);
            if (sizeCart < 0) {
                returnCart();
            } else {
                // Kiểm tra số lượng
                String error = "";
                for(int i = 0; i < publisherName.size(); i++){
                    CardType cardType = cardTypeService.findCarfTypeByPublisherNameAndUnitPrice(publisherName.get(i), Integer.parseInt(unitPrice.get(i)));
                    if(cardType.getInStock() < quantity.get(i)){
                        error += "" + publisherName.get(i) + " mệnh giá " + unitPrice.get(i);
                    }
                }
                if(!error.isEmpty()){
                    session.setAttribute("errorNotStock", "Xin lỗi quý khách " + error +" hiện đang không đủ số lượng trong kho");
                    returnCartAndError();
                }else {
                    // Lưu thông tin đơn hàng vào orders
                    User user = userService.findUserDBByUserSession(userSessionDto);
                    Order order = orderService.saveOrders(user, publisherName, quantity, unitPrice);

                    // Lưu thông tin đơn hàng vào order pending
                    for (int i = 0; i < publisherName.size(); i++) {
                        CardType cardType = cardTypeService.findCarfTypeByPublisherNameAndUnitPrice(publisherName.get(i), Integer.parseInt(unitPrice.get(i)));
                        orderPendingService.saveOrderPending(cardType, quantity.get(i), order);
                    }

                    // Tạo id cho việc thanh toán thành công
                    int random = captchaService.createIDCaptcha();
                    session.setAttribute("successID", random);

                    String description = "Thanh toan don hang";
                    String returnUrl = "http://localhost:8080/success?orderid=" + order.getId() + "&rd=" + random;
                    String cancelUrl = "http://localhost:8080/cart";
                    //Gen order code
                    String currentTimeString = String.valueOf(new Date().getTime());
                    int orderCode = Integer.parseInt(currentTimeString.substring(currentTimeString.length() - 6));
                    // Duyệt từng đơn hàng 1 và cho vào ItemList
                    List<ItemData> itemList = new ArrayList<>();
                    int total = 0;
                    for (int i = 0; i < sizeCart; i++) {
                        int price = Integer.parseInt(unitPrice.get(i));
                        ItemData item = new ItemData(publisherName.get(i), quantity.get(i), price);
                        itemList.add(item);
                        total += quantity.get(i) * price;
                    }
                    PaymentData paymentData = new PaymentData(orderCode, total, description,
                            itemList, cancelUrl, returnUrl);
                    JsonNode data = payOS.createPaymentLink(paymentData);
                    System.out.println("Payment Link Data: " + data.toString());

                    if (data.has("orderCode")) {
                        int paymentId = data.get("orderCode").asInt();
                        System.out.println("Payment orderCode: " + paymentId);
                        session.setAttribute("orderCodeTranstaction", paymentId);
                        String checkoutUrl = data.get("checkoutUrl").asText();
                        httpServletResponse.setHeader("Location", checkoutUrl);
                        httpServletResponse.setStatus(302);
                    } else {
                        System.out.println("Không tìm thấy trường 'orderCode' trong phản hồi từ API");
                        // Xử lý khi không có 'id'
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Có lỗi khi thanh toán");
        }
    }

    @RequestMapping(value = "/payment-info/{orderCode}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String getPaymentInfo(@PathVariable int orderCode) {
        try {
            JsonNode paymentInfo = payOS.getPaymentLinkInfomation(orderCode);
            System.out.println(paymentInfo.toString());  // In dữ liệu JsonNode ra console

            int orderid = (Integer) session.getAttribute("orderId");
            session.removeAttribute("orderId");
            return "redirect:/order/detail?id=" + orderid;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private int sizeCart(List<String> publisherName, List<Integer> quantity, List<String> unitPrice) {
        int sizepublisherName = publisherName.size();
        int sizeQuantity = quantity.size();
        int sizeUnitPrice = unitPrice.size();

        // xác nhận số lượng ở mỗi thuộc tính là bằng nhau
        if (sizepublisherName == sizeQuantity && sizepublisherName == sizeUnitPrice) {
            // trả về số lượng giỏ hàng
            return sizeUnitPrice;
        } else {
            // nếu số lượng k đồng nhất thì quay ngược lại về cart
            return -1;
        }
    }

    public String returnCart() {
        return "redirect:/cart";
    }

    public String returnCartAndError() {
        return "redirect:/cart";
    }

    public String returnLogin() {
        return "redirect:/";
    }
}