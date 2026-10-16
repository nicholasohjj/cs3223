package simpledb.materialize;

import java.util.*;
import simpledb.query.*;

/**
 * The Scan class for the <i>intersect</i> and <i>except</i> operators.
 * Both underlying scans are sorted on all fields.  Each step takes the
 * next group of equal records on the left, counts them (m), counts the
 * equal records on the right (n), and outputs the record as many times
 * as the operation calls for (see SetOpPlan).
 */
public class SetOpScan implements Scan {
   private Scan s1, s2;
   private List<String> fields;
   private RecordComparator comp;
   private boolean intersect, all;
   private boolean hasmore1, hasmore2;
   private Map<String,Constant> current;
   private int pending;

   public SetOpScan(Scan s1, Scan s2, List<String> fields, RecordComparator comp,
                    boolean intersect, boolean all) {
      this.s1 = s1;
      this.s2 = s2;
      this.fields = fields;
      this.comp = comp;
      this.intersect = intersect;
      this.all = all;
      beforeFirst();
   }

   public void beforeFirst() {
      s1.beforeFirst();
      s2.beforeFirst();
      hasmore1 = s1.next();
      hasmore2 = s2.next();
      pending = 0;
   }

   public boolean next() {
      if (pending > 0) {   // another copy of the current record
         pending--;
         return true;
      }
      while (hasmore1) {
         Map<String,Constant> rec = values(s1);
         int m = 0;
         while (hasmore1 && comp.compare(values(s1), rec) == 0) {
            m++;
            hasmore1 = s1.next();
         }
         while (hasmore2 && comp.compare(values(s2), rec) < 0)
            hasmore2 = s2.next();
         int n = 0;
         while (hasmore2 && comp.compare(values(s2), rec) == 0) {
            n++;
            hasmore2 = s2.next();
         }
         int copies = intersect ? (all ? Math.min(m, n) : (n > 0 ? 1 : 0))
                                : (all ? Math.max(m - n, 0) : (n == 0 ? 1 : 0));
         if (copies > 0) {
            current = rec;
            pending = copies - 1;
            return true;
         }
      }
      return false;
   }

   private Map<String,Constant> values(Scan s) {
      Map<String,Constant> rec = new HashMap<>();
      for (String fldname : fields)
         rec.put(fldname, s.getVal(fldname));
      return rec;
   }

   public Constant getVal(String fldname) {
      return current.get(fldname);
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public boolean hasField(String fldname) {
      return fields.contains(fldname);
   }

   public void close() {
      s1.close();
      s2.close();
   }
}
