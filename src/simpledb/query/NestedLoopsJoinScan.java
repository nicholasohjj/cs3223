package simpledb.query;

/**
 * The scan class corresponding to the <i>nested-loops join</i>
 * relational algebra operator.
 * For each record of the outer scan, the inner scan is
 * traversed in full, and only the pairs that satisfy the
 * join predicate are returned.
 */
public class NestedLoopsJoinScan implements Scan {
   private Scan s1, s2;
   private Predicate joinpred;
   private boolean hasouter;

   /**
    * Create a nested-loops join scan having the two
    * underlying scans and the join predicate.
    * @param s1 the outer scan
    * @param s2 the inner scan
    * @param joinpred the predicate that joined records must satisfy
    */
   public NestedLoopsJoinScan(Scan s1, Scan s2, Predicate joinpred) {
      this.s1 = s1;
      this.s2 = s2;
      this.joinpred = joinpred;
      beforeFirst();
   }

   /**
    * Position the scan before its first record:
    * the outer scan is on its first record,
    * and the inner scan is before its first record.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      s1.beforeFirst();
      hasouter = s1.next();
      s2.beforeFirst();
   }

   /**
    * Move to the next pair of records that satisfies the join predicate.
    * The inner scan advances first; when it is exhausted,
    * the outer scan moves to its next record and the inner
    * scan restarts from the beginning.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (hasouter) {
         while (s2.next())
            if (joinpred.isSatisfied(this))
               return true;
         hasouter = s1.next();
         s2.beforeFirst();
      }
      return false;
   }

   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return s2.getInt(fldname);
   }

   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return s2.getString(fldname);
   }

   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return s2.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   public void close() {
      s1.close();
      s2.close();
   }
}
