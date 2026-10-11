package simpledb.parse;

import java.util.List;

public class OrderByElement {
    private String field;
    private boolean asc;

    public OrderByElement(String field, boolean asc) {
        this.field = field;
        this.asc = asc;
    }

    public String field() {
        return field;
    }

    public boolean isAscending() {
        return asc;
    }

    public String toString() {
        return field + (asc ? " asc" : " desc");
    }

    /**
     * Formats a list of sort fields as a comma-separated string,
     * e.g. "gradyear asc, sname desc".
     */
    public static String listString(List<OrderByElement> elements) {
        StringBuilder result = new StringBuilder();
        for (OrderByElement e : elements) {
            if (result.length() > 0)
                result.append(", ");
            result.append(e);
        }
        return result.toString();
    }
}
