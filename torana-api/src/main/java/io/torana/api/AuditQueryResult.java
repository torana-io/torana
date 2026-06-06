package io.torana.api;

import java.util.List;

/**
 * Result of a paginated audit query.
 *
 * <p>This record encapsulates the query results along with pagination metadata, making it easy to
 * implement paginated UIs and APIs.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * AuditQueryResult result = auditTrail.query()
 *     .actionPrefix("order.")
 *     .limit(50)
 *     .offset(100)
 *     .executeWithPagination();
 *
 * System.out.println("Page " + result.pageNumber() + " of " + result.totalPages());
 * System.out.println("Showing " + result.entries().size() + " of " + result.totalCount());
 *
 * if (result.hasNext()) {
 *     // Fetch next page
 * }
 * }</pre>
 *
 * @param entries the audit entries for this page
 * @param totalCount the total number of matching entries (across all pages)
 * @param pageNumber the current page number (0-indexed)
 * @param pageSize the requested page size (limit)
 * @param hasNext whether there are more results available
 */
public record AuditQueryResult(
        List<AuditEntryView> entries,
        long totalCount,
        int pageNumber,
        int pageSize,
        boolean hasNext) {

    /**
     * Creates a query result from raw data.
     *
     * @param entries the entries for this page
     * @param totalCount the total number of matching entries
     * @param offset the offset (number of entries skipped)
     * @param limit the limit (max entries per page)
     * @return the query result
     */
    public static AuditQueryResult of(
            List<AuditEntryView> entries, long totalCount, int offset, int limit) {
        int pageSize = limit > 0 ? limit : entries.size();
        int pageNumber = pageSize > 0 ? offset / pageSize : 0;
        boolean hasNext = (offset + entries.size()) < totalCount;

        return new AuditQueryResult(entries, totalCount, pageNumber, pageSize, hasNext);
    }

    /**
     * Returns the total number of pages.
     *
     * @return total pages
     */
    public int totalPages() {
        if (pageSize == 0) {
            return totalCount == 0 ? 0 : 1;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * Returns whether this is the first page.
     *
     * @return true if this is the first page
     */
    public boolean isFirst() {
        return pageNumber == 0;
    }

    /**
     * Returns whether this is the last page.
     *
     * @return true if this is the last page
     */
    public boolean isLast() {
        return !hasNext;
    }

    /**
     * Returns the number of entries in this page.
     *
     * @return the number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns whether this result is empty.
     *
     * @return true if there are no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
