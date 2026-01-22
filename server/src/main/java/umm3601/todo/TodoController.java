package umm3601.todo;

import static com.mongodb.client.model.Filters.and;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import umm3601.Controller;

public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";

  private final JacksonMongoCollection<Todo> todoCollection;

  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);
    return combinedFilter;
  }

  private Bson constructSortingOrder(Context ctx) {
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    ArrayList<Todo> matchingTodos = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  @Override
  public void addRoutes(Javalin server) {
    server.get(API_TODOS, this::getTodos);
  }
}
