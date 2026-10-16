package simpledb.parse;

import java.util.*;

/**
 * Data for a set operation between two queries:
 * <i>q1</i> union [all] <i>q2</i>, <i>q1</i> intersect [all] <i>q2</i>,
 * or <i>q1</i> except [all] <i>q2</i>.
 * Either query may itself be a set operation.
 * The order by clause, if any, sorts the combined result,
 * and refers to its output fields (those of the left query).
 * <p>
 * It is a kind of QueryData, so it can be passed wherever a query
 * is; its fields, tables and other clauses are those of the left
 * query.  Planners recognise it and plan the set operation.
 */
public class SetQueryData extends QueryData {
   private final QueryData left, right;
   private final String op;
   private final boolean all;

   /**
    * Saves the two queries and the set operation between them.
    * @param left the left query
    * @param op the operation: "union", "intersect" or "except"
    * @param all true if duplicates are kept ("union all" etc.)
    * @param right the right query
    * @param orderByList the order by clause of the combined result
    */
   public SetQueryData(QueryData left, String op, boolean all, QueryData right,
                       List<OrderByElement> orderByList) {
      super(left.fields(), left.tables(), left.aliases(), left.pred(), orderByList,
            left.groupByFields(), left.aggFns(), left.isDistinct());
      this.left = left;
      this.op = op;
      this.all = all;
      this.right = right;
   }

   public QueryData left() {
      return left;
   }

   public QueryData right() {
      return right;
   }

   /**
    * Returns the set operation: "union", "intersect" or "except".
    */
   public String operation() {
      return op;
   }

   /**
    * Returns true if duplicate records are kept.
    */
   public boolean isAll() {
      return all;
   }

   /**
    * Returns the same set operation with the specified order by clause.
    */
   public QueryData withOrderBy(List<OrderByElement> orderByList) {
      return new SetQueryData(left, op, all, right, orderByList);
   }

   public String toString() {
      StringBuilder result = new StringBuilder();
      result.append(left).append(' ').append(op).append(all ? " all " : " ").append(right);
      if (!orderByList().isEmpty())
         result.append(" order by ").append(OrderByElement.listString(orderByList()));
      return result.toString();
   }
}
