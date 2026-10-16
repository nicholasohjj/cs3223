package simpledb.materialize;

import static java.sql.Types.DOUBLE;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>sum</i> aggregation function.
 * @author Edward Sciore
 */
public class SumFn implements AggregationFn {
   private String fldname;
   private int sum;
   private double dsum;
   private boolean isdouble = false;

   /**
    * Create a sum aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public SumFn(String fldname) {
      this.fldname = fldname;
   }

   /**
    * Start a new sum to be the
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = 0;
      dsum = 0;
      processNext(s);
   }

   /**
    * Add the field value in the current record to the running sum.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      if (isdouble)
         dsum += s.getVal(fldname).asDouble();
      else
         sum += s.getVal(fldname).asInt();
   }

   /**
    * Sets the sum of an empty input to 0.
    * @see simpledb.materialize.AggregationFn#processEmpty()
    */
   public void processEmpty() {
      sum = 0;
      dsum = 0;
   }

   /**
    * The sum has the same type as the summed field:
    * int for an int field, double for a double field.
    * @see simpledb.materialize.AggregationFn#addToSchema(Schema, Schema)
    */
   public void addToSchema(Schema outsch, Schema insch) {
      isdouble = insch.type(fldname) == DOUBLE;
      if (isdouble)
         outsch.addDoubleField(fieldName());
      else
         outsch.addIntField(fieldName());
   }

   /**
    * Return the field's name, prepended by "sumof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "sumof" + fldname;
   }

   /**
    * Return the current sum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return isdouble ? new Constant(dsum) : new Constant(sum);
   }

   /**
    * Returns the function call as written in SQL, e.g. "sum(sal)".
    */
   public String toString() {
      return "sum(" + fldname + ")";
   }

   public String argField() {
      return fldname;
   }

   public AggregationFn withField(String fldname) {
      return new SumFn(fldname);
   }
}
