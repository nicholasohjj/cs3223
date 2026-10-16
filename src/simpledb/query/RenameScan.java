package simpledb.query;

import java.util.Map;

/**
 * The scan class corresponding to the <i>rename</i> operator.
 * A field is read by its new name from the underlying
 * scan's field of the old name.
 */
public class RenameScan implements Scan {
   private Scan s;
   private Map<String,String> oldnames;   // new name -> old name

   /**
    * Creates a rename scan.
    * @param s the underlying scan
    * @param oldnames maps a new field name to the underlying field;
    *                 a name not in the map is passed through
    */
   public RenameScan(Scan s, Map<String,String> oldnames) {
      this.s = s;
      this.oldnames = oldnames;
   }

   private String old(String fldname) {
      return oldnames.getOrDefault(fldname, fldname);
   }

   public void beforeFirst() {
      s.beforeFirst();
   }

   public boolean next() {
      return s.next();
   }

   public int getInt(String fldname) {
      return s.getInt(old(fldname));
   }

   public String getString(String fldname) {
      return s.getString(old(fldname));
   }

   public Constant getVal(String fldname) {
      return s.getVal(old(fldname));
   }

   public boolean hasField(String fldname) {
      return oldnames.containsKey(fldname);
   }

   public void close() {
      s.close();
   }
}
