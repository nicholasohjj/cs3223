package simpledb.materialize;

import java.util.*;
import simpledb.parse.OrderByElement;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * The Plan class for the <i>intersect</i> and <i>except</i> operators,
 * with or without "all".  Both inputs are sorted on all of their
 * fields; the scan then merges them a group of equal records at a time.
 * For a record occurring m times on the left and n times on the right:
 * <ul>
 * <li>intersect outputs it once if m, n &gt; 0; intersect all, min(m, n) times;
 * <li>except outputs it once if n = 0; except all, max(m - n, 0) times.
 * </ul>
 * Both inputs must have fields of the same names; the output
 * records are those of the left input.
 */
public class SetOpPlan implements Plan {
   private Plan p1, p2;
   private SortPlan sorted1, sorted2;
   private boolean intersect, all;
   private RecordComparator comp;

   /**
    * Creates an intersect or except node in the query tree.
    * @param tx the calling transaction
    * @param p1 the left input
    * @param p2 the right input, with the same field names
    * @param op "intersect" or "except"
    * @param all true if duplicates are kept
    */
   public SetOpPlan(Transaction tx, Plan p1, Plan p2, String op, boolean all) {
      this.p1 = p1;
      this.p2 = p2;
      this.intersect = op.equals("intersect");
      this.all = all;
      List<OrderByElement> sortfields = new ArrayList<>();
      for (String fldname : p1.schema().fields())
         sortfields.add(new OrderByElement(fldname, true));
      comp = new RecordComparator(sortfields);
      sorted1 = new SortPlan(tx, p1, sortfields);
      sorted2 = new SortPlan(tx, p2, sortfields);
   }

   public Scan open() {
      return new SetOpScan(sorted1.open(), sorted2.open(), p1.schema().fields(),
                           comp, intersect, all);
   }

   /**
    * The cost of reading both sorted inputs once
    * (not including the one-time cost of sorting).
    */
   public int blocksAccessed() {
      return sorted1.blocksAccessed() + sorted2.blocksAccessed();
   }

   /**
    * At most the size of the left input
    * (and of the right input, for intersect).
    */
   public int recordsOutput() {
      int r = p1.recordsOutput();
      return intersect ? Math.min(r, p2.recordsOutput()) : r;
   }

   public int distinctValues(String fldname) {
      return p1.distinctValues(fldname);
   }

   public Schema schema() {
      return p1.schema();
   }

   public List<Plan> children() {
      return List.of(p1, p2);
   }

   public String toString() {
      return (intersect ? "Intersect" : "Except") + (all ? "All" : "") + "(sort-based)";
   }
}
