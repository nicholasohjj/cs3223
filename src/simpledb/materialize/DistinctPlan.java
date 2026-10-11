package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.parse.OrderByElement;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The Plan class for the <i>distinct</i> operator.
 * The input is sorted on all of its fields, so that duplicate
 * records become adjacent and can be skipped in a single pass.
 * Any order by fields are used as the leading sort keys, so the
 * output also comes out in the requested order.
 */
public class DistinctPlan implements Plan {
   private Plan p;
   private Plan sorted;
   private List<OrderByElement> sortfields = new ArrayList<>();

   /**
    * Creates a distinct plan for the specified query.
    * @param tx the calling transaction
    * @param p the plan for the underlying query
    * @param orderByList the order by clause, whose fields must all be in p
    */
   public DistinctPlan(Transaction tx, Plan p, List<OrderByElement> orderByList) {
      this.p = p;
      sortfields.addAll(orderByList);
      Set<String> ordered = new HashSet<>();
      for (OrderByElement e : orderByList)
         ordered.add(e.field());
      for (String fldname : p.schema().fields())
         if (!ordered.contains(fldname))
            sortfields.add(new OrderByElement(fldname, true));
      sorted = new SortPlan(tx, p, sortfields);
   }

   /**
    * Sorts the input, and returns a scan that
    * skips over duplicate records.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      return new DistinctScan(sorted.open(), p.schema().fields());
   }

   /**
    * The cost is that of reading the sorted input
    * (not including the one-time cost of sorting).
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return sorted.blocksAccessed();
   }

   /**
    * Estimates the number of distinct records as the number of
    * combinations of distinct field values, but no more than
    * the number of input records.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      long combos = 1;
      for (String fldname : p.schema().fields())
         combos = Math.min(combos * p.distinctValues(fldname), Integer.MAX_VALUE);
      return (int) Math.min(combos, p.recordsOutput());
   }

   public int distinctValues(String fldname) {
      return p.distinctValues(fldname);
   }

   public Schema schema() {
      return p.schema();
   }

   public List<Plan> children() {
      return List.of(p);
   }

   public String toString() {
      return "Distinct(sort on " + OrderByElement.listString(sortfields) + ")";
   }
}
