package simpledb.plan;

import java.util.List;
import simpledb.query.Scan;
import simpledb.query.UnionScan;
import simpledb.record.Schema;

/**
 * The Plan class for the <i>union all</i> operator: the records
 * of the first input followed by those of the second.
 * Both inputs must have fields of the same names (the planner
 * renames the second input's fields to match the first's).
 * Union without "all" adds a distinct operator on top.
 */
public class UnionPlan implements Plan {
   private Plan p1, p2;
   private Schema schema;

   /**
    * Creates a union all node in the query tree.
    * @param p1 the first input
    * @param p2 the second input
    * @param schema the output schema (which may widen an int
    *               field to a double, or lengthen a varchar)
    */
   public UnionPlan(Plan p1, Plan p2, Schema schema) {
      this.p1 = p1;
      this.p2 = p2;
      this.schema = schema;
   }

   public Scan open() {
      return new UnionScan(p1.open(), p2.open());
   }

   public int blocksAccessed() {
      return p1.blocksAccessed() + p2.blocksAccessed();
   }

   public int recordsOutput() {
      return p1.recordsOutput() + p2.recordsOutput();
   }

   public int distinctValues(String fldname) {
      return p1.distinctValues(fldname) + p2.distinctValues(fldname);
   }

   public Schema schema() {
      return schema;
   }

   public List<Plan> children() {
      return List.of(p1, p2);
   }

   public String toString() {
      return "UnionAll";
   }
}
