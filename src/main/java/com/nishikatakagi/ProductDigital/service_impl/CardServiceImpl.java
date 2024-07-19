package com.nishikatakagi.ProductDigital.service_impl;

import com.nishikatakagi.ProductDigital.dto.CardDTO;
import com.nishikatakagi.ProductDigital.dto.CardUpdateDTO;
import com.nishikatakagi.ProductDigital.dto.UserSessionDto;
import com.nishikatakagi.ProductDigital.model.Card;
import com.nishikatakagi.ProductDigital.model.CardType;
import com.nishikatakagi.ProductDigital.model.User;
import com.nishikatakagi.ProductDigital.repository.CardRepository;
import com.nishikatakagi.ProductDigital.repository.CardTypeRepository;
import com.nishikatakagi.ProductDigital.repository.UserRepository;
import com.nishikatakagi.ProductDigital.service.CardService;
import com.nishikatakagi.ProductDigital.service.CardTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CardServiceImpl implements CardService {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardTypeService cardTypeService;

    @Autowired
    UserRepository userRepository;
    @Autowired
    private CardTypeRepository cardTypeRepository;

    @Override
    public List<Card> findAllCards() {
        return cardRepository.findAll();
    }

    @Override
    public Card findById(int id) {
        Optional<Card> optionalCardType = cardRepository.findById(id);
        return optionalCardType.orElseThrow();
    }

    @Override
    public void saveCard(CardDTO cardDTO, Integer createdBy) {
        Card card = new Card();
        card.setCardNumber(cardDTO.getCardNumber());
        card.setSeriNumber(cardDTO.getSeriNumber());
        card.setExpiryDate(cardDTO.getExpiryDate());
        card.setCardType(cardDTO.getCardType());
        card.setCreatedBy(createdBy);
        Date currentTime = Date.valueOf(LocalDateTime.now().toLocalDate());
        card.setCreatedDate(currentTime);
        card.setIsDeleted(false);
        cardRepository.save(card);
    }

    @Override
    public void updateCard(CardUpdateDTO cardDTO, Integer updatedBy) {
        Card card = findById(cardDTO.getId());
        Date currentTime = Date.valueOf(LocalDateTime.now().toLocalDate());

        card.setCardNumber(cardDTO.getCardNumber());
        card.setSeriNumber(cardDTO.getSeriNumber());
        card.setExpiryDate(cardDTO.getExpiryDate());
        CardType cardType = cardTypeService.findById(cardDTO.getCardTypeId());
        card.setCardType(cardType);
        card.setLastUpdated(currentTime);
        card.setUpdatedBy(updatedBy);
        cardRepository.save(card);
    }

    @Override
    public Page<Card> findAllCards(Integer pageNo, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return cardRepository.findAll(pageable);
    }

    @Override
    public List<Card> getCardFollowQuantityAndCardID(int quantity, CardType cardType) {
        List<Card> listCard = cardRepository.findByCardTypeAndIsDeletedOrderByExpiryDateAsc(cardType, false);
        List<Card> listCardCustomerOrder = new ArrayList<>();
        for(int i = 0; i < quantity; i++){
            listCardCustomerOrder.add(listCard.get(i));
            listCard.get(i).setIsDeleted(true);
            cardRepository.save(listCard.get(i));
        }
        return listCardCustomerOrder;
    }

    @Override
    public void setActiveById(int id, UserSessionDto userDTO, boolean toDelete) {
        Card c = findById(id);
        User user = userRepository.findUserByUsername(userDTO.getUsername());
        c.setIsDeleted(toDelete);
        Date currentTime = Date.valueOf(LocalDateTime.now().toLocalDate());
        if (toDelete) {
            c.setDeletedDate(currentTime);
            c.setDeletedBy(user.getId());
            cardRepository.save(c);
        }


    }
    public List<Card>cardList(){
        return  cardRepository.getAllCart();
    }


    public void addCards(InputStream inputStream, Integer createdByDefault, List<String> messages) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        int successCount = 0;
        int failCount = 0;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // Kiểm tra header
            Row headerRow = sheet.getRow(0);
            String[] expectedHeaders = {"Card Type ID", "Serial Number", "Card Number", "Expiry Date"};
            boolean headerValid = true;
            for (int i = 0; i < expectedHeaders.length; i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null || !expectedHeaders[i].equals(formatter.formatCellValue(cell))) {
                    messages.add("Tên cột không đúng tại vị trí: " + (i + 1) + ". Mong đợi: " + expectedHeaders[i]);
                    headerValid = false;
                }
            }

            if (!headerValid) {
                messages.add("Lỗi: Tên cột không hợp lệ, không thể xử lý file.");
                return; // Dừng lại nếu header không đúng
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header row

                // Validate columns
                String cardTypeIdStr = formatter.formatCellValue(row.getCell(0));
                String seriNumber = formatter.formatCellValue(row.getCell(1));
                String cardNumber = formatter.formatCellValue(row.getCell(2));
                String expiryDateStr = formatter.formatCellValue(row.getCell(3));

                if (cardTypeIdStr == null || cardTypeIdStr.trim().isEmpty() ||
                        seriNumber == null || seriNumber.trim().isEmpty() ||
                        cardNumber == null || cardNumber.trim().isEmpty() ||
                        expiryDateStr == null || expiryDateStr.trim().isEmpty()) {
                    messages.add("Hàng " + (row.getRowNum() + 1) + ": Thiếu dữ liệu cần thiết.");
                    failCount++;
                    continue;
                }

                Integer cardTypeId;
                try {
                    cardTypeId = Integer.parseInt(cardTypeIdStr);
                } catch (NumberFormatException e) {
                    messages.add("Hàng " + (row.getRowNum() + 1) + ": ID loại thẻ không hợp lệ - " + cardTypeIdStr);
                    failCount++;
                    continue;
                }

                CardType cardType = cardTypeRepository.findById(cardTypeId).orElse(null);
                if (cardType == null) {
                    messages.add("Hàng " + (row.getRowNum() + 1) + ": Không tìm thấy loại thẻ với ID - " + cardTypeId);
                    failCount++;
                    continue;
                }

                java.util.Date utilDate;
                try {
                    utilDate = dateFormat.parse(expiryDateStr);
                } catch (ParseException e) {
                    messages.add("Hàng " + (row.getRowNum() + 1) + ": Định dạng ngày không hợp lệ - " + expiryDateStr);
                    failCount++;
                    continue;
                }

                java.sql.Date sqlDate = new java.sql.Date(utilDate.getTime());

                // Check for duplicate (cardTypeId + seriNumber)
                boolean isDuplicate = cardRepository.existsByCardTypeAndSeriNumber(cardType, seriNumber);
                if (isDuplicate) {
                    messages.add("Hàng " + (row.getRowNum() + 1) + ": Trùng lặp thẻ - Seri: " + seriNumber + ", ID loại thẻ: " + cardTypeId);
                    failCount++;
                    continue;
                }

                // Save card
                Card newCard = new Card();
                newCard.setCardType(cardType);
                newCard.setSeriNumber(seriNumber);
                newCard.setCardNumber(cardNumber);
                newCard.setExpiryDate(sqlDate);
                newCard.setIsDeleted(false);
                newCard.setCreatedBy(createdByDefault);
                newCard.setCreatedDate(new java.sql.Date(new java.util.Date().getTime()));
                cardRepository.save(newCard);

                successCount++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            messages.add("Lỗi xử lý tệp Excel: " + e.getMessage());
            failCount++;
        }

        // Display success and fail messages
        messages.add("Nhập thành công " + successCount + " thẻ.");
        messages.add("Nhập thất bại " + failCount + " thẻ với các lỗi sau:");
        for (String message : messages) {
            System.out.println(message); // Print all messages
        }
    }




    public void exportCardsToExcel(String fileName) {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            Sheet sheet = workbook.createSheet("Cards");
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("MM/dd/yyyy"));

            // Define header titles - excluding "ID"
            String[] headers = {"Card Type ID", "Serial Number", "Card Number",
                    "Expiry Date", "Is Deleted", "Deleted Date", "Deleted By",
                    "Created Date", "Created By", "Last Updated", "Updated By"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Fetching card data from database
            List<Card> cards = cardRepository.findAll();
            int rowIdx = 1;
            for (Card card : cards) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue((double) card.getCardType().getId()); // Card Type ID
                row.createCell(1).setCellValue(card.getSeriNumber()); // Serial Number
                row.createCell(2).setCellValue(card.getCardNumber()); // Card Number
                Cell expiryDateCell = row.createCell(3);
                expiryDateCell.setCellValue(card.getExpiryDate());
                expiryDateCell.setCellStyle(dateStyle); // Expiry Date
                row.createCell(4).setCellValue(card.getIsDeleted()); // Is Deleted
                Cell deletedDateCell = row.createCell(5);
                if (card.getDeletedDate() != null) {
                    deletedDateCell.setCellValue(card.getDeletedDate());
                    deletedDateCell.setCellStyle(dateStyle); // Deleted Date
                }
                Cell deletedByCell = row.createCell(6);
                if (card.getDeletedBy() != null) {
                    deletedByCell.setCellValue((double) card.getDeletedBy());
                } else {
                    deletedByCell.setCellValue("N/A"); // Deleted By
                }
                Cell createdDateCell = row.createCell(7);
                createdDateCell.setCellValue(card.getCreatedDate());
                createdDateCell.setCellStyle(dateStyle); // Created Date
                row.createCell(8).setCellValue((double) card.getCreatedBy()); // Created By
                Cell lastUpdatedCell = row.createCell(9);
                if (card.getLastUpdated() != null) {
                    lastUpdatedCell.setCellValue(card.getLastUpdated());
                    lastUpdatedCell.setCellStyle(dateStyle); // Last Updated
                }
                Cell updatedByCell = row.createCell(10);
                if (card.getUpdatedBy() != null) {
                    updatedByCell.setCellValue((double) card.getUpdatedBy());
                } else {
                    updatedByCell.setCellValue("N/A"); // Updated By
                }
            }

            // Auto size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Workbook exportCardsToExcel() {
        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet("Cards");
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("MM/dd/yyyy"));

            String[] headers = {"Card Type ID", "Serial Number", "Card Number", "Expiry Date", "Is Deleted", "Deleted Date", "Deleted By", "Created Date", "Created By", "Last Updated", "Updated By"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            List<Card> cards = cardRepository.findAll();
            int rowIdx = 1;
            for (Card card : cards) {
                Row row = sheet.createRow(rowIdx++);
                fillRowWithData(row, card, dateStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Optionally rethrow as a custom exception or handle as needed
        }
        return workbook;
    }
    private void fillRowWithData(Row row, Card card, CellStyle dateStyle) {
        // Method to fill row data, reduces code duplication
        row.createCell(0).setCellValue((double) card.getCardType().getId()); // Card Type ID
        row.createCell(1).setCellValue(card.getSeriNumber()); // Serial Number
        row.createCell(2).setCellValue(card.getCardNumber()); // Card Number

        Cell expiryDateCell = row.createCell(3);
        if (card.getExpiryDate() != null) {
            expiryDateCell.setCellValue(card.getExpiryDate());
            expiryDateCell.setCellStyle(dateStyle); // Expiry Date
        }

        row.createCell(4).setCellValue(card.getIsDeleted()); // Is Deleted

        Cell deletedDateCell = row.createCell(5);
        if (card.getDeletedDate() != null) {
            deletedDateCell.setCellValue(card.getDeletedDate());
            deletedDateCell.setCellStyle(dateStyle); // Deleted Date
        } else {
            deletedDateCell.setCellValue("N/A");
        }

        Cell deletedByCell = row.createCell(6);
        if (card.getDeletedBy() != null) {
            deletedByCell.setCellValue((double) card.getDeletedBy()); // Deleted By
        } else {
            deletedByCell.setCellValue("N/A");
        }

        Cell createdDateCell = row.createCell(7);
        createdDateCell.setCellValue(card.getCreatedDate());
        createdDateCell.setCellStyle(dateStyle); // Created Date

        row.createCell(8).setCellValue((double) card.getCreatedBy()); // Created By

        Cell lastUpdatedCell = row.createCell(9);
        if (card.getLastUpdated() != null) {
            lastUpdatedCell.setCellValue(card.getLastUpdated());
            lastUpdatedCell.setCellStyle(dateStyle); // Last Updated
        } else {
            lastUpdatedCell.setCellValue("N/A");
        }

        Cell updatedByCell = row.createCell(10);
        if (card.getUpdatedBy() != null) {
            updatedByCell.setCellValue((double) card.getUpdatedBy()); // Updated By
        } else {
            updatedByCell.setCellValue("N/A");
        }
    }
}
