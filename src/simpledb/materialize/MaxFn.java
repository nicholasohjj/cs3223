package simpledb.materialize;

import static java.sql.Types.*;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>max</i> aggregation function.
 * @author Edward Sciore
 */
public class MaxFn implements AggregationFn {
   private String fldname;
   private Constant val;
   private int type = INTEGER;
   
   /**
    * Create a max aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public MaxFn(String fldname) {
      this.fldname = fldname;
   }
   
   /**
    * Start a new maximum to be the 
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      val = s.getVal(fldname);
   }
   
   /**
    * Replace the current maximum by the field value
    * in the current record, if it is higher.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) > 0)
         val = newval;
   }
   
   /**
    * Sets the maximum of an empty input to 0 (or the empty string),
    * since SimpleDB has no null.
    * @see simpledb.materialize.AggregationFn#processEmpty()
    */
   public void processEmpty() {
      val = (type == INTEGER) ? new Constant(0)
          : (type == DOUBLE) ? new Constant(0.0)
          : new Constant("");
   }

   /**
    * The maximum has the same type as the aggregated field.
    * @see simpledb.materialize.AggregationFn#addToSchema(Schema, Schema)
    */
   public void addToSchema(Schema outsch, Schema insch) {
      type = insch.type(fldname);
      outsch.addField(fieldName(), insch.type(fldname), insch.length(fldname));
   }

   /**
    * Return the field's name, prepended by "maxof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "maxof" + fldname;
   }
   
   /**
    * Return the current maximum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return val;
   }

   /**
    * Returns the function call as written in SQL, e.g. "max(sal)".
    */
   public String toString() {
      return "max(" + fldname + ")";
   }

   public String argField() {
      return fldname;
   }

   public AggregationFn withField(String fldname) {
      return new MaxFn(fldname);
   }
}
