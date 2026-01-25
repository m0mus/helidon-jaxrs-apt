package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;

/**
 * Bean class for aggregating form parameters.
 * Used with @BeanParam to test form parameter aggregation.
 */
public class FormDataParams {

    @FormParam("username")
    private String username;

    @FormParam("email")
    private String email;

    @FormParam("age")
    @DefaultValue("0")
    private Integer age;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "username=" + username + ",email=" + email + ",age=" + age;
    }
}
