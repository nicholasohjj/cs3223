package simpledb.parse;

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
}
