import entities.Book;
import entities.Borrow;
import entities.Card;
import queries.*;
import utils.DBInitializer;
import utils.DatabaseConnector;

import java.lang.Thread.State;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryManagementSystemImpl implements LibraryManagementSystem {

    private final DatabaseConnector connector;

    public LibraryManagementSystemImpl(DatabaseConnector connector) {
        this.connector = connector;
    }



    /*
    store the book to databse , set the bookId of the book if success
    @param book,without bookId
    @return Apiresult, if success, bookId of the book will be set, otherwise bookId will not be set and return message will describe the error
    */
    @Override
    public ApiResult storeBook(Book book) {
        Connection conn = connector.getConn();
        try {
            String sql = "insert into book (category, title, press, publish_year, author, price, stock) values (?, ?, ?, ?, ?, ?, ?)";
            /*return the generated keys */
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, book.getCategory());
            pstmt.setString(2, book.getTitle());
            pstmt.setString(3, book.getPress());
            pstmt.setInt(4, book.getPublishYear());
            pstmt.setString(5, book.getAuthor());
            pstmt.setDouble(6, book.getPrice());
            pstmt.setInt(7, book.getStock());
            pstmt.executeUpdate();
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                book.setBookId(generatedKeys.getInt(1));
            }
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }



    /*
    modify the stock of the book
    @param bookId the id of the book to be modified
    @param deltaStock the change of the stock, can be positive or negative, but the final stock cannot be negative
    @return ApiResult, if success, the stock of the book will be modified, otherwise return message will describe the error and stock will not be modified
    */
    @Override
    public ApiResult incBookStock(int bookId, int deltaStock) {
        Connection conn = connector.getConn();
        try {
            // chenck if the book exists and the final stock is not negative
            String checksql = "SELECT stock FROM book WHERE book_id = ?";
            PreparedStatement checkPstmt = conn.prepareStatement(checksql);
            checkPstmt.setInt(1, bookId);
            ResultSet rs = checkPstmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "No matching book found.");
            }
            int currentStock = rs.getInt("stock");
            if (currentStock + deltaStock < 0) {
                return new ApiResult(false, "Stock cannot be negative.");
            }
            // update the stock
            String sql = "update book set stock = stock + ? where book_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, deltaStock);
            pstmt.setInt(2, bookId);
            pstmt.executeUpdate();
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }



    /*
    store many books to database, set the bookId of the books if success
    @param books, a list of books without bookId
    @return Apiresult, if success, bookId of the books will be set, otherwise bookId will not be set and return message will describe the error
    */
    @Override
    public ApiResult storeBook(List<Book> books) {
        Connection conn = connector.getConn();
        try {
            String sql = "insert into book (category, title, press, publish_year, author, price, stock) values (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (Book book : books) {
                pstmt.setString(1, book.getCategory());
                pstmt.setString(2, book.getTitle());
                pstmt.setString(3, book.getPress());
                pstmt.setInt(4, book.getPublishYear());
                pstmt.setString(5, book.getAuthor());
                pstmt.setDouble(6, book.getPrice());
                pstmt.setInt(7, book.getStock());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            // get the generated keys and set the bookId of the books
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            int index = 0;
            while (generatedKeys.next()) {
                books.get(index++).setBookId(generatedKeys.getInt(1));
            }
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }



    /*
    remove a book from database
    @param bookId the id of the book to be removed
    @return ApiResult, if success, the book will be removed, otherwise return message will describe the error
    */
    @Override
    public ApiResult removeBook(int bookId) {
        Connection conn = connector.getConn();
        try {
            // if the book is currently borrowed and not returned, it cannot be removed
            String checksql = "SELECT * FROM borrow WHERE book_id = ? AND return_time = 0";
            PreparedStatement checkPstmt = conn.prepareStatement(checksql);
            checkPstmt.setInt(1, bookId);
            ResultSet rs = checkPstmt.executeQuery();
            if (rs.next()) {
                return new ApiResult(false, "Cannot remove book that is currently borrowed and not returned.");
            }
            // delete the book
            String sql = "delete from book where book_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, bookId);
            int rowsAffected =  pstmt.executeUpdate();
            // if no rows affected, it means no matching book found
            if (rowsAffected!=1) {
                return new ApiResult(false, "No matching book found.");
            }
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }




    /*
    modify the information of a book
    @param book the book with updated information
    @return ApiResult, if success, the book information will be updated, otherwise return message will describe the error
    */
    @Override
    public ApiResult modifyBookInfo(Book book) {
        Connection conn = connector.getConn();
        try {
            String sql = "update book set category = ?, title = ?, press = ?, publish_year = ?, author = ?, price = ? where book_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, book.getCategory());
            pstmt.setString(2, book.getTitle());
            pstmt.setString(3, book.getPress());
            pstmt.setInt(4, book.getPublishYear());
            pstmt.setString(5, book.getAuthor());
            pstmt.setDouble(6, book.getPrice());
            pstmt.setInt(7, book.getBookId());
            pstmt.executeUpdate();
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }




    /*
    query books based on given conditions
    @param conditions the query conditions
    @return ApiResult, if success, the query results will be returned, otherwise return message will describe the error
    */
    @Override
    public ApiResult queryBook(BookQueryConditions conditions) {
        Connection conn = connector.getConn();

        try {
            StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM book WHERE 1=1");
            List<Object> params = new java.util.ArrayList<>();
            if (conditions.getCategory() != null && !conditions.getCategory().isEmpty()) {
            sqlBuilder.append(" AND category = ?");
            params.add(conditions.getCategory());
        }
        if (conditions.getTitle() != null && !conditions.getTitle().isEmpty()) {
            sqlBuilder.append(" AND title LIKE ?");
            params.add("%" + conditions.getTitle() + "%");
        }
        if (conditions.getPress() != null && !conditions.getPress().isEmpty()) {
            sqlBuilder.append(" AND press LIKE ?");
            params.add("%" + conditions.getPress() + "%");
        }
        if (conditions.getMinPublishYear() != null) {
            sqlBuilder.append(" AND publish_year >= ?");
            params.add(conditions.getMinPublishYear());
        }
        if (conditions.getMaxPublishYear() != null) {
            sqlBuilder.append(" AND publish_year <= ?");
            params.add(conditions.getMaxPublishYear());
        }
        if (conditions.getAuthor() != null && !conditions.getAuthor().isEmpty()) {
            sqlBuilder.append(" AND author LIKE ?");
            params.add("%" + conditions.getAuthor() + "%");
        }
        if (conditions.getMinPrice() != null) {
            sqlBuilder.append(" AND price >= ?");
            params.add(conditions.getMinPrice());
        }
        if (conditions.getMaxPrice() != null) {
            sqlBuilder.append(" AND price <= ?");
            params.add(conditions.getMaxPrice());
        }
        
        /* sort the results based on sortBy and sortOrder, if sortBy is not bookId, 
        then sort by bookId in ascending order as tie breaker*/ 
        sqlBuilder.append(" order by ").append(conditions.getSortBy().getValue())
                .append(" ").append(conditions.getSortOrder().getValue());
        // if sortBy is not bookId, then sort by bookId in ascending order as tie breaker
        if (conditions.getSortBy() != Book.SortColumn.BOOK_ID) {
            sqlBuilder.append(", book_id asc");
        }
        PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString());
        for (int i = 0; i < params.size(); i++) {
            pstmt.setObject(i + 1, params.get(i));
        }
        ResultSet rs = pstmt.executeQuery();
        
        List<Book> books = new ArrayList<>();
        while (rs.next()) {
        Book book = new Book();
        book.setBookId(rs.getInt("book_id"));
        book.setCategory(rs.getString("category"));
        book.setTitle(rs.getString("title"));
        book.setPress(rs.getString("press"));
        book.setPublishYear(rs.getInt("publish_year"));
        book.setAuthor(rs.getString("author"));
        book.setPrice(rs.getDouble("price"));
        book.setStock(rs.getInt("stock"));
        books.add(book);
        }
        BookQueryResults results = new BookQueryResults(books);
        return new ApiResult(true, results);
        }
        catch (Exception e) {
            return new ApiResult(false, e.getMessage());
        }
    }




    /*
    store the borrow record to database
    @param borrow the borrow record without borrowId and returnTime
    @return ApiResult, if success, the borrow record will be stored and borrowId will
    */
    @Override
    public ApiResult borrowBook(Borrow borrow) {
        Connection conn = connector.getConn();
        String checkCardSql = "select 1 from borrow where card_id = ? and book_id = ? and return_time = 0";
        String decStockSql = "update book set stock = stock - 1 where book_id = ? and stock > 0";
        String insertSql = "insert into borrow(card_id, book_id, borrow_time, return_time) values(?, ?, ?, 0)";
        try {
            // check if the card has already borrowed this book and has not returned it yet
            PreparedStatement pstmt = conn.prepareStatement(checkCardSql);
            pstmt.setInt(1, borrow.getCardId());
            pstmt.setInt(2, borrow.getBookId());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new ApiResult(false, "This card has already borrowed this book and has not returned it yet.");
            }
            // update the stock of the book
            pstmt = conn.prepareStatement(decStockSql);
            pstmt.setInt(1, borrow.getBookId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                return new ApiResult(false, "Book is out of stock.");
            }

            // insert the borrow record
            pstmt = conn.prepareStatement(insertSql);
            pstmt.setInt(1, borrow.getCardId());
            pstmt.setInt(2, borrow.getBookId());
            pstmt.setLong(3, borrow.getBorrowTime());
            pstmt.executeUpdate();
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }




    /*
    store the return record to database, which is to update the corresponding borrow record's returnTime
    @param borrow the borrow record with cardId, bookId and returnTime, borrowId
    @return ApiResult, if success, the borrow record will be updated with returnTime, otherwise return message will describe the error and borrow record will not be updated
    */
    @Override
    public ApiResult returnBook(Borrow borrow) {
        Connection conn = connector.getConn();
        try {
            String checkCardSql = "select borrow_time from borrow where card_id = ? and book_id = ? and return_time = 0";
            String sql = "update borrow set return_time = ? where card_id = ? and book_id = ? and return_time = 0";
            // check if there is a borrow record with the cardId and bookId and return_time = 0, which means the book has not been returned yet
            PreparedStatement pstmt = conn.prepareStatement(checkCardSql);
            pstmt.setInt(1, borrow.getCardId());
            pstmt.setInt(2, borrow.getBookId());
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "No matching borrow record found or the book has already been returned.");
            }
            if(borrow.getReturnTime() <= rs.getLong("borrow_time")) {
                return new ApiResult(false, "Return time cannot be earlier than borrow time.");
            }
            // update the borrow record with return time
            pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, borrow.getReturnTime());
            pstmt.setInt(2, borrow.getCardId());
            pstmt.setInt(3, borrow.getBookId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                return new ApiResult(false, "No matching borrow record found or the book has already been returned.");
            }
            // update the stock of the book, increase the stock by 1
            String incStockSql = "update book set stock = stock + 1 where book_id = ?";
            pstmt = conn.prepareStatement(incStockSql);
            pstmt.setInt(1, borrow.getBookId());
            pstmt.executeUpdate();
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }

    /*
    show the borrow history of a card
    @param cardId the card ID
    @return ApiResult, if success, the borrow history will be returned, otherwise return message will describe the error
    */
    @Override
    public ApiResult showBorrowHistory(int cardId) {
        Connection conn = connector.getConn();
            String sql = "select b.card_id, b.book_id, bk.category, bk.title, bk.press, bk.publish_year, " +
                "bk.author, bk.price, b.borrow_time, b.return_time " +
                "from borrow b join book bk on b.book_id = bk.book_id " +
                "where b.card_id = ? order by b.borrow_time desc, b.book_id asc";

        List<BorrowHistories.Item> items = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cardId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BorrowHistories.Item item = new BorrowHistories.Item();
                    item.setCardId(rs.getInt("card_id"));
                    item.setBookId(rs.getInt("book_id"));
                    item.setCategory(rs.getString("category"));
                    item.setTitle(rs.getString("title"));
                    item.setPress(rs.getString("press"));
                    item.setPublishYear(rs.getInt("publish_year"));
                    item.setAuthor(rs.getString("author"));
                    item.setPrice(rs.getDouble("price"));
                    item.setBorrowTime(rs.getLong("borrow_time"));
                    item.setReturnTime(rs.getLong("return_time"));
                    items.add(item);
                }
            }
            commit(conn);
            return new ApiResult(true, null, new BorrowHistories(items));
        }
        catch (Exception e) {
            return new ApiResult(false, e.getMessage());
        }


    }



    /*
    register a new card
    @param card the card to be registered
    @return ApiResult, if success, the card will be registered and cardId will be set, otherwise return message will describe the error
    */
    @Override
    public ApiResult registerCard(Card card) {
       Connection conn = connector.getConn();
         try {
            String sql = "insert into card (name,department,type) values (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, card.getName());
            pstmt.setString(2, card.getDepartment());
            pstmt.setString(3, card.getType().getStr());
            pstmt.executeUpdate();
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                card.setCardId(generatedKeys.getInt(1));
            }
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }




    /*
    remove a card from database
    @param cardId the card ID
    @return ApiResult, if success, the card will be removed, otherwise return message will describe the error
    */
    @Override
    public ApiResult removeCard(int cardId) {
        Connection conn = connector.getConn();
        try {
            String checksql = "SELECT * FROM borrow WHERE card_id = ? AND return_time = 0";
            String sql = "delete from card where card_id = ?";
            // if the card has currently borrowed books that have not been returned, it cannot be removed
            PreparedStatement checkPstmt = conn.prepareStatement(checksql);
            checkPstmt.setInt(1, cardId);
            ResultSet rs = checkPstmt.executeQuery();
            if (rs.next()) {
                return new ApiResult(false, "Cannot remove card that has currently borrowed books not returned.");
            }
            // delete the card
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, cardId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected != 1) {
                return new ApiResult(false, "No matching card found.");
            }
            commit(conn);
        }
        catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }


    /*
    show all the cards in the library system, sorted by card ID in ascending order
    @return ApiResult, if success, the card list will be returned, otherwise return message
    */
    @Override
    public ApiResult showCards() {
       String sql = "select * from card order by card_id asc";
        List<Card> cards = new ArrayList<>();
        Connection conn = connector.getConn();
        try{
            PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Card card = new Card();
                card.setCardId(rs.getInt("card_id"));
                card.setName(rs.getString("name"));
                card.setDepartment(rs.getString("department"));
                card.setType(Card.CardType.values(rs.getString("type")));
                cards.add(card);
            }
            commit(conn);
            return new ApiResult(true, null, new CardList(cards));}
        catch (Exception e) {
            return new ApiResult(false, e.getMessage());
        }
    }


    @Override
    public ApiResult resetDatabase() {
        Connection conn = connector.getConn();
        try {
            Statement stmt = conn.createStatement();
            DBInitializer initializer = connector.getConf().getType().getDbInitializer();
            stmt.addBatch(initializer.sqlDropBorrow());
            stmt.addBatch(initializer.sqlDropBook());
            stmt.addBatch(initializer.sqlDropCard());
            stmt.addBatch(initializer.sqlCreateCard());
            stmt.addBatch(initializer.sqlCreateBook());
            stmt.addBatch(initializer.sqlCreateBorrow());
            stmt.executeBatch();
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }

    private void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void commit(Connection conn) {
        try {
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
