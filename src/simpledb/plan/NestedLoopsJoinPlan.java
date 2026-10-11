package simpledb.plan;

import simpledb.query.NestedLoopsJoinScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/** The Plan class for the <i>nested-loops join</i> operator.
  * Unlike a product followed by a selection, the join
  * predicate is evaluated inside the join loop itself,
  * so it works for any comparison (not just equality).
  */
public class NestedLoopsJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate joinpred;
   private Schema schema = new Schema();

   /**
    * Creates a nested-loops join node in the query tree.
    * @param p1 the outer subquery
    * @param p2 the inner subquery
    * @param joinpred the predicate that joined records must satisfy
    */
   public NestedLoopsJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
      this.p1 = p1;
      this.p2 = p2;
      this.joinpred = joinpred;
      schema.addAll(p1.schema());
      schema.addAll(p2.schema());
   }

   /**
    * Creates a nested-loops join scan for this query.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopsJoinScan(s1, s2, joinpred);
   }

   /**
    * The inner subquery is scanned once per outer record:
    * <pre> B(p1) + R(p1)*B(p2) </pre>
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return p1.blocksAccessed() + (p1.recordsOutput() * p2.blocksAccessed());
   }

   /**
    * Estimates the number of output records as the size of
    * the product, reduced by the join predicate.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      // an empty input can give a reduction factor of 0
      int rf = Math.max(1, joinpred.reductionFactor(this));
      return p1.recordsOutput() * p2.recordsOutput() / rf;
   }

   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   public Schema schema() {
      return schema;
   }
}
