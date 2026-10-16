package simpledb.materialize;

import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>avg</i> aggregation function.
 * SimpleDB only supports integer-valued fields, so the
 * average is computed as an integer (truncating division),
 * the same way that SQL computes AVG over an integer column.
 * @author Edward Sciore
 */
public class AvgFn implements AggregationFn {
   private String fldname;
   private double sum;
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
      sum = s.getVal(fldname).asDouble();
      count = 1;
   }

   /**
    * Add the field value in the current record to the running sum,
    * and increment the count of values seen.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getVal(fldname).asDouble();
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
    * The average is a double, whatever the type of the field.
    * @see simpledb.materialize.AggregationFn#addToSchema(Schema, Schema)
    */
   public void addToSchema(Schema outsch, Schema insch) {
      outsch.addDoubleField(fieldName());
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
      return new Constant(count == 0 ? 0.0 : sum / count);
   }

   /**
    * Returns the function call as written in SQL, e.g. "avg(sal)".
    */
   public String toString() {
      return "avg(" + fldname + ")";
   }

   public String argField() {
      return fldname;
   }

   public AggregationFn withField(String fldname) {
      return new AvgFn(fldname);
   }
}
