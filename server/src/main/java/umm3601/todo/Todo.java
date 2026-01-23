package umm3601.todo;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Todo {

  @ObjectId @Id
  // Creating variables that todo should know of.
  @SuppressWarnings({"MemberName"})
  public String _id;
  public String owner;
  public Boolean status;
  public String body;
  public String category;

  // Checks to make sure the todo is in the todo data, and that it is an actual todo.
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Todo)) {
      return false;
    }
    Todo other = (Todo) obj;
    return _id.equals(other._id);
  }
  // This makes our identical ids hash to the same location.
  @Override
  public int hashCode() {
    return _id.hashCode();
  }

  // Returns the owner of todo, we can use this for debugging.
  @Override
  public String toString() {
    return owner;
  }
}
