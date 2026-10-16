package simpledb.materialize;

import java.util.*;

import simpledb.parse.OrderByElement;
import simpledb.query.*;

/**
 * A comparator for scans.
 * @author Edward Sciore
 */
public class RecordComparator implements Comparator<Scan> {
   private List<OrderByElement> orderByFields;
   
   /**
    * Create a comparator using the specified fields,
    * using the ordering implied by its iterator.
    * @param orderByFields a list of field names
    */
   public RecordComparator(List<OrderByElement> orderByFields) {
      this.orderByFields = orderByFields;
   }
   
   /**
    * Compare the current records of the two specified scans.
    * The sort fields are considered in turn.
    * When a field is encountered for which the records have
    * different values, those values are used as the result
    * of the comparison.
    * If the two records have the same values for all
    * sort fields, then the method returns 0.
    * @param s1 the first scan
    * @param s2 the second scan
    * @return the result of comparing each scan's current record according to the field list
    */
   public int compare(Scan s1, Scan s2) {
      return compare(s1::getVal, s2::getVal);
   }

   /**
    * Compare two records held in memory, as maps from
    * field names to values, in the same way.
    * @param r1 the first record
    * @param r2 the second record
    * @return the result of comparing the records according to the field list
    */
   public int compare(Map<String,Constant> r1, Map<String,Constant> r2) {
      return compare(r1::get, r2::get);
   }

   private int compare(java.util.function.Function<String,Constant> r1,
                       java.util.function.Function<String,Constant> r2) {
      for (OrderByElement e : orderByFields) {
         String fldname = e.field();
         Constant val1 = r1.apply(fldname);
         Constant val2 = r2.apply(fldname);
         int result = val1.compareTo(val2);
         if (result != 0)
            return e.isAscending() ? result: -result;
      }
      return 0;
   }
}
