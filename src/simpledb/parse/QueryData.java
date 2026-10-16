package simpledb.parse;

import java.util.*;

import simpledb.materialize.AggregationFn;
import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private final List<OrderByElement> orderByList;
   private final List<String> fields;
   private final Collection<String> tables;
   private final Predicate pred;
   private final List<String> groupByFields;
   private final List<AggregationFn> aggFns;
   private final boolean distinct;
   private final List<String> aliases;

   /**
    * Saves the field and table list and predicate.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred, List<OrderByElement> orderByList) {
      this(fields, tables, pred, orderByList, new ArrayList<>(), new ArrayList<>());
   }

   /**
    * Saves the field and table list, predicate, order-by list,
    * group-by fields and aggregation functions.
    * @param fields the fields (and aggregate output names) of the select clause,
    *               in the order they should appear in the result
    * @param tables the tables of the from clause
    * @param pred the predicate of the where clause
    * @param orderByList the fields of the order by clause
    * @param groupByFields the fields of the group by clause
    * @param aggFns the aggregation functions appearing in the select clause
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<OrderByElement> orderByList, List<String> groupByFields,
                     List<AggregationFn> aggFns) {
      this(fields, tables, pred, orderByList, groupByFields, aggFns, false);
   }

   /**
    * Saves the clauses of the query, as above, and whether the
    * select clause asked for distinct rows.
    * @param distinct true if the query is a select distinct
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<OrderByElement> orderByList, List<String> groupByFields,
                     List<AggregationFn> aggFns, boolean distinct) {
      this(fields, tables, null, pred, orderByList, groupByFields, aggFns, distinct);
   }

   /**
    * Saves the clauses of the query, as above, and the alias
    * given to each table of the from clause.
    * @param aliases the alias of each table, in the same order as the
    *                tables; null (or a null entry) means the table's own name
    */
   public QueryData(List<String> fields, Collection<String> tables, List<String> aliases,
                     Predicate pred, List<OrderByElement> orderByList, List<String> groupByFields,
                     List<AggregationFn> aggFns, boolean distinct) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.orderByList = orderByList;
      this.groupByFields = groupByFields;
      this.aggFns = aggFns;
      this.distinct = distinct;
      this.aliases = new ArrayList<>();
      Iterator<String> it = (aliases == null) ? null : aliases.iterator();
      for (String tblname : tables) {
         String alias = (it == null) ? null : it.next();
         this.aliases.add(alias == null ? tblname : alias);
      }
   }

   /**
    * Returns the fields mentioned in the select clause.
    * For an aggregate function such as sum(sal), this list
    * contains the aggregate's output field name (e.g. "sumofsal")
    * rather than the raw field being aggregated.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }

   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }

   /**
    * Returns the alias of each table in the from clause, in the
    * same order as tables(); a table without an alias is
    * known by its own name.
    * @return a list of aliases
    */
   public List<String> aliases() {
      return aliases;
   }

   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }

   public List<OrderByElement> orderByList() {
      return orderByList;
   }

   /**
    * Returns the fields mentioned in the group by clause.
    * An empty list means there is no explicit grouping;
    * if aggregate functions are present, they are computed
    * over the entire table as a single group.
    * @return a list of grouping field names
    */
   public List<String> groupByFields() {
      return groupByFields;
   }

   /**
    * Returns the aggregation functions mentioned in the select clause.
    * @return a list of aggregation functions
    */
   public List<AggregationFn> aggFns() {
      return aggFns;
   }

   /**
    * Returns true if the query needs grouping/aggregation processing,
    * i.e. it has an explicit group by clause or at least one
    * aggregate function in its select list.
    */
   public boolean isGrouped() {
      return !groupByFields.isEmpty() || !aggFns.isEmpty();
   }

   /**
    * Returns the same query with the specified order by clause.
    * @param orderByList the new order by clause
    * @return the query, ordered by those fields
    */
   public QueryData withOrderBy(List<OrderByElement> orderByList) {
      return new QueryData(fields, tables, aliases, pred, orderByList, groupByFields, aggFns, distinct);
   }

   /**
    * Returns true if duplicate rows are to be removed from the output.
    * @return true if the query is a select distinct
    */
   public boolean isDistinct() {
      return distinct;
   }

   public String toString() {
      StringBuilder result = new StringBuilder(distinct ? "select distinct " : "select ");
      // an aggregate is written as its function call (e.g. "count(sid)"),
      // not its output field name, so that the text can be parsed again
      Map<String,String> aggtext = new HashMap<>();
      for (AggregationFn fn : aggFns)
         aggtext.put(fn.fieldName(), fn.toString());
      for (String fldname : fields)
         result.append(aggtext.getOrDefault(fldname, fldname)).append(", ");
      result.setLength(result.length() - 2);
      result.append(" from ");
      Iterator<String> alias = aliases.iterator();
      for (String tblname : tables) {
         String a = alias.next();
         result.append(tblname).append(a.equals(tblname) ? "" : " " + a).append(", ");
      }
      result.setLength(result.length() - 2);
      String predstring = pred.toString();
      if (!predstring.isEmpty())
         result.append(" where ").append(predstring);
      if (!groupByFields.isEmpty()) {
         result.append(" group by ");
         for (String fldname : groupByFields)
            result.append(fldname).append(", ");
         result.setLength(result.length() - 2);
      }
      if (!orderByList.isEmpty()) {
         result.append(" order by ");
         for (OrderByElement e : orderByList) {
            result.append(e.field()).append(e.isAscending() ? " asc" : " desc").append(", ");
         }
         result.setLength(result.length() - 2);
      }
      return result.toString();
   }
}
