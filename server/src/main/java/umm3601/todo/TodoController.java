package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// import org.bson.BsonInt32;
// import org.bson.BsonValue;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

@SuppressWarnings({ "MagicNumber" })

// Controller that manages request for info about todos.
public class TodoController implements Controller {

  // Creating our path for our all todos.
  private static final String API_TODOS = "/api/todos";

  // Creating our path for a todo by id
  private static final String API_TODO_BY_ID = "/api/todos/{id}";

  static final String LIMIT_KEY = "limit";

  private final JacksonMongoCollection<Todo> todoCollection;

  // Constructing a controller for todos.
  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /* Construct a Bson filter document to use in the `find` method based on the
   *    query parameters from the context.
   *
   * Right now it filters for nothing as we are returning all todos.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *    to filter the database collection of users
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);
    return combinedFilter;
  }

  private Integer constructLimit(Context ctx) {
    if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      Integer targetLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
      .check(it -> it > 0, "Limit must be greater than 0")
      .get();

      return targetLimit;
    } else {
      return 300; // this is how many todos I believe are in the database.
      //             Using this number ensuring all todos still show.
    }
  }

  /*
   * Construct a Bson sorting document to use in the `sort` method based on the
   *    query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   *    parameters and constructs a sorting document that will sort todos by
   *    the specified field in the specified order.
   * If the `sortby` query parameter is not present, it defaults to "owner". If the `sortorder`
   *    query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *    to sort the database collection of users
   */
  private Bson constructSortingOrder(Context ctx) {
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  /*
   * Set the JSON body of the response to be a list of all the todos returned from the database
   *    that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);
    Integer limitInput = constructLimit(ctx);

    // All three of the find, sort, and into steps happen "in order listed" inside the
    // database. MongoDB is going to find the todos with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingTodos = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .limit(limitInput)
      .into(new ArrayList<>());

    // Set the JSON body of the response to be the list of todos returned by the database.
    // This calls result(jsonString), and also sets content type to json
    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  /*
   * Set the JSON body of the response to be the single user
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodoByID(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found");
    } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /*
   * Sets up routes for the `todo` collection endpoints.
   * A TodoController instance handles the todo endpoints,
   * and the addRoutes method adds the routes to this controller.
   *
   * @param server The Javalin server instance
   */
  @Override
  public void addRoutes(Javalin server) {
    // List todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);
    // Get the specified todo by ID
    server.get(API_TODO_BY_ID, this::getTodoByID);
  }
}
