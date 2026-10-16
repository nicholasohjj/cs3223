package simpledb.materialize;

import static java.sql.Types.INTEGER;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>min</i> aggregation function.
 * @author Edward Sciore
 */
public class MinFn implements AggregationFn {
   private String fldname;
   private Constant val;
   private boolean isint = true;

   /**
    * Create a min aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public MinFn(String fldname) {
      this.fldname = fldname;
   }

   /**
    * Start a new minimum to be the
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      val = s.getVal(fldname);
   }

   /**
    * Replace the current minimum by the field value
    * in the current record, if it is lower.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) < 0)
         val = newval;
   }

   /**
    * Sets the minimum of an empty input to 0 (or the empty string),
    * since SimpleDB has no null.
    * @see simpledb.materialize.AggregationFn#processEmpty()
    */
   public void processEmpty() {
      val = isint ? new Constant(0) : new Constant("");
   }

   /**
    * The minimum has the same type as the aggregated field.
    * @see simpledb.materialize.AggregationFn#addToSchema(Schema, Schema)
    */
   public void addToSchema(Schema outsch, Schema insch) {
      isint = insch.type(fldname) == INTEGER;
      outsch.addField(fieldName(), insch.type(fldname), insch.length(fldname));
   }

   /**
    * Return the field's name, prepended by "minof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "minof" + fldname;
   }

   /**
    * Return the current minimum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return val;
   }

   /**
    * Returns the function call as written in SQL, e.g. "min(sal)".
    */
   public String toString() {
      return "min(" + fldname + ")";
   }
}
