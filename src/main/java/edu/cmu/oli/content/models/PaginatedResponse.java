package edu.cmu.oli.content.models;

import com.google.gson.annotations.Expose;

import java.util.List;

/**
 * Basic POJO for Paginated Responses
 *
 * @param <T> Result Type
 */
public class PaginatedResponse<T> {
    @Expose()
    private long offset;

    @Expose()
    private long limit;

    @Expose()
    private String order;

    @Expose()
    private String orderBy;

    @Expose()
    private long numResults;

    @Expose()
    private long totalResults;

    @Expose()
    private List<T> results;

    public PaginatedResponse(long offset, long limit, String order, String orderBy, long totalResults, List<T> results) {
        this.offset = offset;
        this.limit = limit;
        this.order = order;
        this.orderBy = orderBy;
        this.numResults = results.size();
        this.totalResults = totalResults;
        this.results = results;
    }
}
