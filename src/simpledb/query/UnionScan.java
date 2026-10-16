package simpledb.query;

/**
 * The scan class corresponding to the <i>union all</i> operator:
 * it returns every record of the first scan, then every
 * record of the second.
 */
public class UnionScan implements Scan {
   private Scan s1, s2, current;

   public UnionScan(Scan s1, Scan s2) {
      this.s1 = s1;
      this.s2 = s2;
      beforeFirst();
   }

   public void beforeFirst() {
      s1.beforeFirst();
      s2.beforeFirst();
      current = s1;
   }

   public boolean next() {
      if (current == s1) {
         if (s1.next())
            return true;
         current = s2;
      }
      return s2.next();
   }

   public int getInt(String fldname) {
      return current.getInt(fldname);
   }

   public String getString(String fldname) {
      return current.getString(fldname);
   }

   public Constant getVal(String fldname) {
      return current.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s1.hasField(fldname);
   }

   public void close() {
      s1.close();
      s2.close();
   }
}
