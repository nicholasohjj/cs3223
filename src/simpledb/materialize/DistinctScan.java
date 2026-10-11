package simpledb.materialize;

import java.util.*;
import simpledb.query.*;

/**
 * The Scan class for the <i>distinct</i> operator.
 * The underlying scan is sorted on all of the fields,
 * so a record is a duplicate exactly when it equals
 * the record returned just before it.
 */
public class DistinctScan implements Scan {
   private Scan s;
   private List<String> fields;
   private GroupValue prev;

   /**
    * Create a distinct scan over the specified sorted scan.
    * @param s the sorted underlying scan
    * @param fields the fields that records are compared on
    */
   public DistinctScan(Scan s, List<String> fields) {
      this.s = s;
      this.fields = fields;
      beforeFirst();
   }

   public void beforeFirst() {
      s.beforeFirst();
      prev = null;
   }

   /**
    * Move to the next record that differs from the previous one.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (s.next()) {
         GroupValue gv = new GroupValue(s, fields);
         if (prev == null || !prev.equals(gv)) {
            prev = gv;
            return true;
         }
      }
      return false;
   }

   public void close() {
      s.close();
   }

   public Constant getVal(String fldname) {
      return s.getVal(fldname);
   }

   public int getInt(String fldname) {
      return s.getInt(fldname);
   }

   public String getString(String fldname) {
      return s.getString(fldname);
   }

   public boolean hasField(String fldname) {
      return s.hasField(fldname);
   }
}
