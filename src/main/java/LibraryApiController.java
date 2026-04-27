import entities.Book;
import entities.Borrow;
import entities.Card;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import queries.ApiResult;
import queries.BookQueryConditions;
import queries.BookQueryResults;
import queries.BorrowHistories;
import queries.CardList;
import queries.SortOrder;
import utils.DatabaseConnector;

import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@RequestMapping
public class LibraryApiController {
    private final DatabaseConnector databaseConnector;
    private final LibraryManagementSystem libraryManagementSystem;

    public LibraryApiController( LibraryManagementSystem libraryManagementSystem ,DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
        this.libraryManagementSystem = libraryManagementSystem;
    }


    /*
    select the book based on conditions

    @param category the category of the book
    @param title the title of the book
    @param press the publisher of the book
    @param author the author of the book

    @return the list of books that satisfy the conditions
    */
   @GetMapping("/book")
   public ResponseEntity<?> queryBooks(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String press,
       @RequestParam(required = false) Integer minPublishYear,
       @RequestParam(required = false) Integer maxPublishYear,
       @RequestParam(required = false) String author,
       @RequestParam(required = false) Double minPrice,
       @RequestParam(required = false) Double maxPrice,
       @RequestParam(required = false) String sortBy,
       @RequestParam(required = false) String sortOrder
   ) {
       BookQueryConditions conditions = new BookQueryConditions();
       if(category != null) {
           conditions.setCategory(category);
       }
       if(title != null) {
           conditions.setTitle(title);
       }
       if(press != null) {
           conditions.setPress(press);
       }
       if(minPublishYear != null) {
           conditions.setMinPublishYear(minPublishYear);
       }
       if(maxPublishYear != null) {
           conditions.setMaxPublishYear(maxPublishYear);
       }
       if(author != null) {
           conditions.setAuthor(author);
       }
       if(minPrice != null) {
           conditions.setMinPrice(minPrice);
       }
       if(maxPrice != null) {
           conditions.setMaxPrice(maxPrice);
       }
       if(sortBy != null && !sortBy.trim().isEmpty()) {
           Book.SortColumn sortColumn = parseSortColumn(sortBy);
           if(sortColumn == null) {
               return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid sortBy: " + sortBy);
           }
           conditions.setSortBy(sortColumn);
       }
       if(sortOrder != null && !sortOrder.trim().isEmpty()) {
           SortOrder parsedSortOrder = parseSortOrder(sortOrder);
           if(parsedSortOrder == null) {
               return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid sortOrder: " + sortOrder);
           }
           conditions.setSortOrder(parsedSortOrder);
       }
       ApiResult results = libraryManagementSystem.queryBook(conditions);
       /*
       if the operation is not successful , return the error message with status code 500 ONLY!
       */
       if(!results.ok){
            return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(results.message);
       }
       return ResponseEntity.ok(results.payload);
    }

    private Book.SortColumn parseSortColumn(String sortBy) {
        String normalized = sortBy.trim();
        for (Book.SortColumn column : Book.SortColumn.values()) {
            if (column.name().equalsIgnoreCase(normalized) || column.getValue().equalsIgnoreCase(normalized)) {
                return column;
            }
        }
        return null;
    }

    private SortOrder parseSortOrder(String sortOrder) {
        String normalized = sortOrder.trim();
        for (SortOrder order : SortOrder.values()) {
            if (order.name().equalsIgnoreCase(normalized) || order.getValue().equalsIgnoreCase(normalized)) {
                return order;
            }
        }
        return null;
    }





    /*
    create a new book record in the database
    @param req the request body containing the book information
    @return the bookId map
    */
    @PostMapping("/book")
    @ResponseBody
    public ResponseEntity<?> createBook(@RequestBody BookCreateRequest req) {
        Book book = new Book(req.category, req.title, req.press, req.publishYear, req.author, req.price, req.stock);
        ApiResult result = libraryManagementSystem.storeBook(book);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookId", book.getBookId());
        return ResponseEntity.ok(payload);
    }




    @PostMapping(value = "/book/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)//定义接受数据类型
    @ResponseBody
    public ResponseEntity<?> importBooks(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("仅支持 .json 文件导入");
        }

        try {
            List<Book> books = parseBooksFromJson(file);
            if (books.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("未解析到可导入的图书数据");
            }

            ApiResult result = libraryManagementSystem.storeBook(books);
            if (!result.ok) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("count", books.size());
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private List<Book> parseBooksFromJson(MultipartFile file) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BookCreateRequest[] requests = mapper.readValue(file.getInputStream(), BookCreateRequest[].class);
        List<Book> books = new ArrayList<>();
        if (requests == null) {
            return books;
        }
        for (int i = 0; i < requests.length; i++) {
            books.add(toBook(requests[i], i + 1));
        }
        return books;
    }

    private Book toBook(BookCreateRequest req, int rowNo) {
        if (req == null) {
            throw new IllegalArgumentException("第 " + rowNo + " 行数据为空");
        }
        if (req.category == null || req.category.trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 category 不能为空");
        }
        if (req.title == null || req.title.trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 title 不能为空");
        }
        if (req.press == null || req.press.trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 press 不能为空");
        }
        if (req.author == null || req.author.trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 author 不能为空");
        }
        if (req.stock < 0) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 stock 不能小于 0");
        }
        if (req.price < 0) {
            throw new IllegalArgumentException("第 " + rowNo + " 行 price 不能小于 0");
        }
        return new Book(req.category.trim(), req.title.trim(), req.press.trim(), req.publishYear, req.author.trim(), req.price, req.stock);
    }



    /*
    modify the book information in the database, the book is identified by the id in the path variable
    @param id the id of the book to be modified
    @param req the request body containing the new book information
    @return the status of the operation
    */
    @PutMapping("/book/{id}")
    @ResponseBody
    public ResponseEntity<?> modifyBook(@PathVariable("id") int id, @RequestBody BookUpdateRequest req) {
        Book book = new Book(req.category, req.title, req.press, req.publishYear, req.author, req.price, 0);
        book.setBookId(id);
        ApiResult result = libraryManagementSystem.modifyBookInfo(book);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }




    /*
    delete the book identified by the id in the path variable from the database
    @param id the id of the book to be deleted
    @return the status of the operation
    */
    @DeleteMapping("/book/{id}")
    @ResponseBody
    public ResponseEntity<?> removeBook(@PathVariable("id") int id) {
        ApiResult result = libraryManagementSystem.removeBook(id);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }




    /*
    increase the stock of the book identified by the id in the path variable
    @param id the id of the book to have its stock increased
    @param delta the amount by which to increase the stock
    @return the status of the operation
    */
    @PostMapping("/book/{id}/stock")
    @ResponseBody
    public ResponseEntity<?> increaseStock(@PathVariable("id") int id, @RequestParam("delta") int delta) {
        ApiResult result = libraryManagementSystem.incBookStock(id, delta);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }




    /*
    show all cards in the database
    @return the list of cards
    */
    @GetMapping("/card")
    @ResponseBody
    public ResponseEntity<?> showCards() {
        ApiResult result = libraryManagementSystem.showCards();
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok(result.payload);
    }



    /*
    create a new card in the database
    @param req the request body containing the card information
    @return the status of the operation
    */
    @PostMapping("/card")
    @ResponseBody
    public ResponseEntity<?> createCard(@RequestBody CardRequest req) {
        Card card = new Card();
        card.setName(req.name);
        card.setDepartment(req.department);
        card.setType(parseCardType(req.type));

        ApiResult result = libraryManagementSystem.registerCard(card);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok(result.payload);
    }



    /*
    register the card identified by the id in the path variable
    @param id the id of the card to be updated
    @param req the request body containing the updated card information
    @return the status of the operation
    */
    @PutMapping("/card/{id}")
    @ResponseBody
    public ResponseEntity<?> registerCard(@PathVariable("id") int id, @RequestBody CardRequest req) {
        Card card = new Card();
        card.setCardId(id);
        card.setName(req.name);
        card.setDepartment(req.department);
        card.setType(parseCardType(req.type));
        ApiResult result = libraryManagementSystem.registerCard(card);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);    
        }
        return ResponseEntity.ok(result.payload);
    }



    /*
    delete the card identified by the id in the path variable from the database
    @param id the id of the card to be deleted
    @return the status of the operation
    
    */
    @DeleteMapping("/card/{id}")
    @ResponseBody
    public ResponseEntity<?> removeCard(@PathVariable("id") int id) {
        ApiResult result = libraryManagementSystem.removeCard(id);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }


    /*
    borrow a book
    @param req the request body containing the borrow information
    @return the status of the operation
    */
    @PostMapping("/borrow")
    @ResponseBody
    public ResponseEntity<?> borrow(@RequestBody BorrowRequest req) {
        Borrow borrow = new Borrow(req.bookId, req.cardId);
        borrow.resetBorrowTime();
        ApiResult result = libraryManagementSystem.borrowBook(borrow);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }


    /*
    return a borrowed book
    @param req the request body containing the return information
    @return the status of the operation
    */
    @PostMapping("/borrow/return")
    @ResponseBody
    public ResponseEntity<?> doReturn(@RequestBody BorrowRequest req) {
        Borrow borrow = new Borrow(req.bookId, req.cardId);
        borrow.resetReturnTime();
        ApiResult result = libraryManagementSystem.returnBook(borrow);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok().build();
    }



    /*
    show the borrow history for a specific card
    @param cardId the ID of the card for which to show borrow history
    @return the list of borrow records
    */
    @GetMapping("/borrow")
    @ResponseBody
    public ResponseEntity<?> histories(@RequestParam("cardID") int cardId) {
        ApiResult result = libraryManagementSystem.showBorrowHistory(cardId);
        if (!result.ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message);
        }
        return ResponseEntity.ok(result.payload);
    }




    private Card.CardType parseCardType(String type) {
        if (type == null) {
            return Card.CardType.Student;
        }
        String value = type.trim().toLowerCase(Locale.ROOT);
        if ("教师".equals(type) || "teacher".equals(value) || "t".equals(value)) {
            return Card.CardType.Teacher;
        }
        return Card.CardType.Student;
    }





    //Define request body classes for better request parsing

    public static class CardRequest {
        public String name;
        public String department;
        public String type;
    }

    public static class BorrowRequest {
        public int cardId;
        public int bookId;
    }

    public static class BookCreateRequest {
        public String category;
        public String title;
        public String press;
        public int publishYear;
        public String author;
        public double price;
        public int stock;
    }

    public static class BookUpdateRequest {
        public String category;
        public String title;
        public String press;
        public int publishYear;
        public String author;
        public double price;
    }

}
