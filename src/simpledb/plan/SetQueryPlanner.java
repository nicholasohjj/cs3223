package simpledb.plan;

import static java.sql.Types.*;
import java.util.*;
import simpledb.materialize.*;
import simpledb.parse.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * Plans a set operation (union, intersect or except) between two
 * queries.  Each query is planned by the given query planner; the
 * two must be <i>union compatible</i>: they have the same number of
 * fields, and corresponding fields are both strings or both numbers
 * (an int and a double combine as a double).  The output fields have
 * the names of the left query's fields.
 */
public class SetQueryPlanner {
   private SetQueryPlanner() {}

   /**
    * Creates a plan for the specified set operation.
    * @param data the set operation
    * @param qp the planner for the two queries
    * @param tx the calling transaction
    * @return the plan for the combined result
    */
   public static Plan createPlan(SetQueryData data, QueryPlanner qp, Transaction tx) {
      Plan left = qp.createPlan(data.left(), tx);
      Plan right = qp.createPlan(data.right(), tx);
      String op = data.operation() + (data.isAll() ? " all" : "");
      Schema lsch = left.schema(), rsch = right.schema();
      List<String> lfields = lsch.fields(), rfields = rsch.fields();
      if (lfields.size() != rfields.size())
         throw new BadQueryException("each side of " + op + " must select the same number of fields, not "
               + lfields.size() + " and " + rfields.size());

      // give the right side's fields the left side's names, and find the output types
      Map<String,String> rename = new HashMap<>();
      Schema outsch = new Schema();
      for (int i=0; i<lfields.size(); i++) {
         String lf = lfields.get(i), rf = rfields.get(i);
         rename.put(rf, lf);
         int ltype = lsch.type(lf), rtype = rsch.type(rf);
         boolean lstring = (ltype == VARCHAR), rstring = (rtype == VARCHAR);
         if (lstring != rstring)
            throw new BadQueryException("cannot " + op + " " + lf + " (" + typeName(ltype) + ") with "
                  + rf + " (" + typeName(rtype) + ")");
         if (lstring)
            outsch.addStringField(lf, Math.max(lsch.length(lf), rsch.length(rf)));
         else if (ltype == DOUBLE || rtype == DOUBLE)
            outsch.addDoubleField(lf);
         else
            outsch.addIntField(lf);
      }
      right = new RenamePlan(right, rename);

      Plan p;
      if (data.operation().equals("union")) {
         p = new UnionPlan(left, right, outsch);
         if (!data.isAll())
            p = new DistinctPlan(tx, p, new ArrayList<>());
      }
      else
         p = new SetOpPlan(tx, left, right, data.operation(), data.isAll());

      // the order by clause sorts the combined result, by its output fields
      for (OrderByElement e : data.orderByList())
         if (!lfields.contains(e.field()))
            throw new BadQueryException("order by field " + e.field() + " is not a field of the "
                  + data.operation() + " (its fields are " + String.join(", ", lfields) + ")");
      if (!data.orderByList().isEmpty())
         p = new SortPlan(tx, p, data.orderByList());
      return p;
   }

   static String typeName(int type) {
      return (type == INTEGER) ? "int" : (type == DOUBLE) ? "double" : "varchar";
   }
}
