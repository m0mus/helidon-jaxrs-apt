package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;

/**
 * Bean class for aggregating search parameters.
 * Used with @BeanParam to test parameter aggregation.
 */
public class SearchParams {

    @QueryParam("q")
    private String query;

    @QueryParam("page")
    @DefaultValue("1")
    private Integer page;

    @QueryParam("size")
    @DefaultValue("10")
    private Integer size;

    @HeaderParam("X-Sort-Order")
    @DefaultValue("asc")
    private String sortOrder;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "q=" + query + ",page=" + page + ",size=" + size + ",sort=" + sortOrder;
    }
}
