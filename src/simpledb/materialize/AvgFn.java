package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>avg</i> aggregation function.
 * SimpleDB only supports integer-valued fields, so the
 * average is computed as an integer (truncating division),
 * the same way that SQL computes AVG over an integer column.
 * @author Edward Sciore
 */
public class AvgFn implements AggregationFn {
   private String fldname;
   private int sum;
   private int count;

   /**
    * Create an avg aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public AvgFn(String fldname) {
      this.fldname = fldname;
   }

   /**
    * Start a new average, using the field value
    * in the current record as the first value summed.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asInt();
      count = 1;
   }

   /**
    * Add the field value in the current record to the running sum,
    * and increment the count of values seen.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getVal(fldname).asInt();
      count++;
   }

   /**
    * Sets the average of an empty input to 0, since SimpleDB has no null.
    * @see simpledb.materialize.AggregationFn#processEmpty()
    */
   public void processEmpty() {
      sum = 0;
      count = 0;
   }

   /**
    * Return the field's name, prepended by "avgof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "avgof" + fldname;
   }

   /**
    * Return the current average, computed as sum/count.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return new Constant(count == 0 ? 0 : sum / count);
   }
}
