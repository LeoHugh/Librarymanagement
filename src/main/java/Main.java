import entities.Book;
import entities.Borrow;
import entities.Card;
import queries.ApiResult;
import queries.BookQueryConditions;
import queries.BookQueryResults;
import queries.BorrowHistories;
import queries.CardList;
import utils.ConnectConfig;
import utils.DatabaseConnector;

import java.util.logging.Logger;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        DatabaseConnector connector = null;
        try {
            // parse connection config from "resources/application.yaml"
            ConnectConfig conf = new ConnectConfig();
            log.info("Success to parse connect config. " + conf.toString());

            // connect to database
            connector = new DatabaseConnector(conf);
            boolean connStatus = connector.connect();
            if (!connStatus) {
                log.severe("Failed to connect database.");
                System.exit(1);
            }

            LibraryManagementSystem library = new LibraryManagementSystemImpl(connector);

            // Reset DB to run the demo repeatedly.
            check(library.resetDatabase(), "reset database");

            Book book = new Book("Computer Science", "Database System Concepts",
                    "Machine Industry Press", 2023, "Mike", 88.80, 5);
            check(library.storeBook(book), "store book");
            log.info("Stored book: " + book);

            Card card = new Card();
            card.setName("DemoUser");
            card.setDepartment("Computer Science");
            card.setType(Card.CardType.Student);
            check(library.registerCard(card), "register card");
            log.info("Registered card: " + card);

            Borrow borrow = new Borrow(book, card);
            borrow.resetBorrowTime();
            check(library.borrowBook(borrow), "borrow book");
            log.info("Borrow succeeded: " + borrow);

            borrow.resetReturnTime();
            check(library.returnBook(borrow), "return book");
            log.info("Return succeeded: " + borrow);

            ApiResult queryBookResult = library.queryBook(new BookQueryConditions());
            check(queryBookResult, "query books");
            BookQueryResults books = (BookQueryResults) queryBookResult.payload;
            log.info("Query books count=" + books.getCount());
            for (Book b : books.getResults()) {
                log.info("  " + b);
            }

            ApiResult showCardsResult = library.showCards();
            check(showCardsResult, "show cards");
            CardList cards = (CardList) showCardsResult.payload;
            log.info("Cards count=" + cards.getCount());
            for (Card c : cards.getCards()) {
                log.info("  " + c);
            }

            ApiResult historiesResult = library.showBorrowHistory(card.getCardId());
            check(historiesResult, "show borrow history");
            BorrowHistories histories = (BorrowHistories) historiesResult.payload;
            log.info("Borrow history count=" + histories.getCount());
            for (BorrowHistories.Item item : histories.getItems()) {
                log.info("  " + item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connector != null) {
                if (connector.release()) {
                    log.info("Success to release connection.");
                } else {
                    log.warning("Failed to release connection.");
                }
            }
        }
    }

    private static void check(ApiResult result, String operation) {
        if (result == null || !result.ok) {
            String msg = result == null ? "null result" : result.message;
            throw new RuntimeException("Failed to " + operation + ": " + msg);
        }
    }

}
