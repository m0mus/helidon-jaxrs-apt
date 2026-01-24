package io.helidon.examples.jaxrs.apt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Example JAX-RS resource processed at compile time.
 *
 * <p>The annotation processor generates {@code UserResource$$JaxRsRouting} with:
 * <ul>
 *   <li>No runtime reflection</li>
 *   <li>Direct method calls</li>
 *   <li>Optimized parameter extraction</li>
 *   <li>GraalVM native-image compatible</li>
 * </ul>
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Map<Long, User> users = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    static {
        User user1 = new User(idGenerator.getAndIncrement(), "Alice", "alice@example.com");
        User user2 = new User(idGenerator.getAndIncrement(), "Bob", "bob@example.com");
        users.put(user1.id(), user1);
        users.put(user2.id(), user2);
    }

    /**
     * GET /users - List all users with optional name filtering.
     */
    @GET
    public Map<Long, User> listUsers(@QueryParam("name") String nameFilter) {
        if (nameFilter != null && !nameFilter.isEmpty()) {
            return users.entrySet().stream()
                    .filter(e -> e.getValue().name().contains(nameFilter))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return users;
    }

    /**
     * GET /users/{id} - Get a specific user.
     *
     * @throws NotFoundException if user does not exist
     */
    @GET
    @Path("/{id}")
    public User getUser(@PathParam("id") Long id) {
        User user = users.get(id);
        if (user == null) {
            throw new NotFoundException("User not found: " + id);
        }
        return user;
    }

    /**
     * POST /users - Create a new user.
     *
     * @return the created user with assigned ID
     */
    @POST
    public User createUser(User user) {
        Long id = idGenerator.getAndIncrement();
        User newUser = new User(id, user.name(), user.email());
        users.put(id, newUser);
        return newUser;
    }

    /**
     * PUT /users/{id} - Update an existing user.
     *
     * @throws NotFoundException if user does not exist
     */
    @PUT
    @Path("/{id}")
    public User updateUser(@PathParam("id") Long id, User user) {
        if (!users.containsKey(id)) {
            throw new NotFoundException("User not found: " + id);
        }
        User updatedUser = new User(id, user.name(), user.email());
        users.put(id, updatedUser);
        return updatedUser;
    }

    /**
     * DELETE /users/{id} - Delete a user.
     *
     * @throws NotFoundException if user does not exist
     */
    @DELETE
    @Path("/{id}")
    public void deleteUser(@PathParam("id") Long id) {
        User removed = users.remove(id);
        if (removed == null) {
            throw new NotFoundException("User not found: " + id);
        }
    }

    /**
     * GET /users/count - Count users.
     */
    @GET
    @Path("/count")
    @Produces(MediaType.TEXT_PLAIN)
    public String countUsers() {
        return "Total users: " + users.size();
    }
}
