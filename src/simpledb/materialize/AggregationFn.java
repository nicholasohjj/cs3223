package simpledb.materialize;

import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The interface implemented by aggregation functions.
 * Aggregation functions are used by the <i>groupby</i> operator.
 * @author Edward Sciore
 */
public interface AggregationFn {
   
   /**
    * Use the current record of the specified scan
    * to be the first record in the group.
    * @param s the scan to aggregate over.
    */
   void processFirst(Scan s);
   
   /**
    * Use the current record of the specified scan
    * to be the next record in the group.
    * @param s the scan to aggregate over.
    */
   void processNext(Scan s);
   
   /**
    * Set the aggregation value for an input with no records.
    * This only arises when there is no group by clause,
    * since SQL then still returns one row.
    */
   void processEmpty();

   /**
    * Add the new aggregation field to the schema of the
    * grouped output.  By default the field is an int.
    * @param outsch the schema of the grouped output
    * @param insch the schema of the records being aggregated
    */
   default void addToSchema(Schema outsch, Schema insch) {
      outsch.addIntField(fieldName());
   }

   /**
    * Return the field that this function aggregates over.
    * @return the name of the aggregated field
    */
   String argField();

   /**
    * Return the same function applied to a different field,
    * e.g. after resolving the field's name to its qualified form.
    * @param fldname the field to aggregate over
    * @return a new aggregation function
    */
   AggregationFn withField(String fldname);

   /**
    * Return the name of the new aggregation field.
    * @return the name of the new aggregation field
    */
   String fieldName();
   
   /**
    * Return the computed aggregation value.
    * @return the computed aggregation value
    */
   Constant value();
}
