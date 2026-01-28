package umm3601.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
//import io.javalin.json.JavalinJackson;
import io.javalin.http.NotFoundResponse;
import io.javalin.validation.Validation;
import io.javalin.validation.Validator;
//import umm3601.todo.TodoController;

@SuppressWarnings({ "MagicNumber" })

class TodoControllerSpec {

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.
  private TodoController todoController;

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests.
  private ObjectId magicjohnsonsID;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  // Used to translate between JSON and POJOs.
  //private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /* Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  // Heres the teardown method where we close the connection.
  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito
    // annotations @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "chef curry")
            .append("status", true)
            .append("body", "number 1 shooter")
            .append("category", "basketball"));
    testTodos.add(
        new Document()
            .append("owner", "Kobe")
            .append("status", false)
            .append("body", "Forever in our hearts")
            .append("category", "mamba"));
    testTodos.add(
        new Document()
            .append("owner", "shaq")
            .append("status", false)
            .append("body", "absolute unit")
            .append("category", "giant"));

    magicjohnsonsID = new ObjectId();
    Document magicjohnson = new Document()
        .append("_id", magicjohnsonsID)
        .append("owner", "Lakers")
        .append("status", true)
        .append("body", "number 1 passer")
        .append("category", "bamboozler");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(magicjohnson);

    todoController = new TodoController(db);
  }

  // all our test for added funtionailrty
  @Test
  void addsRoutes() {
    Javalin mockServer = mock(Javalin.class);
    todoController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(2)).get(any(), any());
    verify(mockServer, never()).post(any(), any());
    verify(mockServer, never()).delete(any(), any());
  }

  @Test
  void canGetAllTodos() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Check that the database collection holds the same number of documents
    // as the size of the captured List<Todo>
    assertEquals(
        db.getCollection("todos").countDocuments(),
        todoArrayListCaptor.getValue().size());
  }

  @Test
  void getTodoWithExistentId() throws IOException {
    String id = magicjohnsonsID.toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    todoController.getTodoByID(ctx);

    verify(ctx).json(todoCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals("Lakers", todoCaptor.getValue().owner);
    assertEquals(magicjohnsonsID.toHexString(), todoCaptor.getValue()._id);
  }

  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodoByID(ctx);
    });

    assertEquals("The requested todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodoByID(ctx);
    });

    assertEquals("The requested todo was not found", exception.getMessage());
  }

  @Test
  void canGetTodosLimitedTo2() throws IOException {
    Integer limit = 2;
    String limitString = "2";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.LIMIT_KEY, Arrays.asList(new String[] {limitString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.LIMIT_KEY)).thenReturn(limitString);

    Validation validation = new Validation();
    Validator<Integer> validator = validation.validator(TodoController.LIMIT_KEY, Integer.class, limitString);
    when(ctx.queryParamAsClass(TodoController.LIMIT_KEY, Integer.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we are only showing 2 todos
    assertEquals(limit, todoArrayListCaptor.getValue().size());
  }

  @Test
  void canGetTodosWithStatus() throws IOException {
    //Boolean targetStatus = true;
    String targetStatusString = "complete";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {targetStatusString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(targetStatusString);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.STATUS_KEY, String.class, targetStatusString);
    when(ctx.queryParamAsClass(TodoController.STATUS_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` have status true.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(true, todo.status);
    }
  }

  @Test
  void canGetTodosWithStatusIncomplete() throws IOException {
    String targetStatusString = "incomplete";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {targetStatusString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(targetStatusString);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.STATUS_KEY, String.class, targetStatusString);
    when(ctx.queryParamAsClass(TodoController.STATUS_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` have status true.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(false, todo.status);
    }
  }

  @Test
  void canGetTodosWithCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    String categoryString = "basketball";
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {categoryString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, categoryString);
    when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    assertEquals(1, todoArrayListCaptor.getValue().size());
  }

  @Test
  void canGetTodosWithOwner() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    String ownerString = "Lakers";
    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {ownerString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, ownerString);
    when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    assertEquals(1, todoArrayListCaptor.getValue().size());
    }

  @Test
  void canGetBodyWithString() throws IOException {
    Integer bodyNumber = 2;

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.BODY_KEY, Arrays.asList(new String[] {"number 1"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.BODY_KEY)).thenReturn("number 1");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we have two bodies with the phrase number 1.
    assertEquals(bodyNumber, todoArrayListCaptor.getValue().size());
  }

  @Test
  void canGetSortedTodosByCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.SORTBY_KEY, Arrays.asList(new String[] {"category"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.SORTBY_KEY)).thenReturn("category");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    List<String> sortedList = new ArrayList<>();

    for (Todo todo : todoArrayListCaptor.getValue()) {
      sortedList.add(todo.category);
    }

    List<String> myList = new ArrayList<>();
    myList.add("bamboozler");
    myList.add("basketball");
    myList.add("giant");
    myList.add("mamba");

    assertEquals(myList, sortedList);
  }

  @Test
  void getTodosByOwnerAndCategory() throws IOException {
    String targetOwnerString = "Lakers";
    String targetCategoryString = "bamboozler";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {targetOwnerString}));
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {targetCategoryString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn(targetOwnerString);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, targetCategoryString);
    when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn(targetCategoryString);

    Validation validation2 = new Validation();
    Validator<String> validator2 = validation2.validator(TodoController.OWNER_KEY, String.class, targetOwnerString);
    when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator2);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn(targetOwnerString);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(1, todoArrayListCaptor.getValue().size());
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwnerString, todo.owner);
      assertEquals(targetCategoryString, todo.category);
    }
  }
}
