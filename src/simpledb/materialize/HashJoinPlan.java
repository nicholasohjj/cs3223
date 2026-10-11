package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The Plan class for the <i>hash join</i> (partition-based) operator.
 * Both inputs are partitioned on their join field into k temporary
 * tables using the same hash function, so that matching records
 * always land in partitions with the same number.
 * Each partition of the smaller (build) input is then loaded into
 * an in-memory hash table and probed with the corresponding
 * partition of the other (probe) input.
 */
public class HashJoinPlan implements Plan {
   private Transaction tx;
   private Plan p1, p2;
   private String fldname1, fldname2;
   private Schema sch = new Schema();

   /**
    * Creates a hash join plan for the two specified queries.
    * @param tx the calling transaction
    * @param p1 the LHS query plan
    * @param p2 the RHS query plan
    * @param fldname1 the LHS join field
    * @param fldname2 the RHS join field
    */
   public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      this.p1 = p1;
      this.p2 = p2;
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;
      sch.addAll(p1.schema());
      sch.addAll(p2.schema());
   }

   /**
    * Returns true if the two inputs can be hash joined with a single
    * partitioning pass.  With M available buffers, partitioning uses
    * one input buffer and up to M-2 output buffers, and each build
    * partition must then fit in M-2 buffers, so the smaller input
    * can be at most (M-2)^2 blocks.
    * @param tx the calling transaction
    * @param p1 the LHS query plan
    * @param p2 the RHS query plan
    * @return true if a single-pass hash join is possible
    */
   public static boolean fits(Transaction tx, Plan p1, Plan p2) {
      int avail = tx.availableBuffs() - 2;
      int smaller = Math.min(materializedSize(tx, p1), materializedSize(tx, p2));
      return avail >= 1 && smaller <= avail * avail;
   }

   /**
    * Partitions both inputs, and returns a hash join scan
    * over the partitions.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      // the LHS is usually the larger subtree (it holds the joins so far),
      // so it is partitioned first, and the number of partitions is chosen
      // from the buffers still available once its scan is open
      Scan src1 = p1.open();
      int k = Math.max(1, tx.availableBuffs() - 1);
      List<TempTable> parts1 = partition(src1, p1.schema(), fldname1, k);
      Scan src2 = p2.open();
      List<TempTable> parts2 = partition(src2, p2.schema(), fldname2, k);

      // build on the smaller input, probe with the larger
      if (materializedSize(tx, p2) <= materializedSize(tx, p1))
         return new HashJoinScan(parts1, fldname1, parts2, fldname2, p2.schema());
      else
         return new HashJoinScan(parts2, fldname2, parts1, fldname1, p1.schema());
   }

   /**
    * Returns the cost of the hash join, including partitioning:
    * reading both inputs, writing their partitions, and reading
    * the partitions back once each.
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      int parts = materializedSize(tx, p1) + materializedSize(tx, p2);
      return p1.blocksAccessed() + p2.blocksAccessed() + 2 * parts;
   }

   /**
    * Return the number of records in the join.
    * Assuming uniform distribution, the formula is:
    * <pre> R(join(p1,p2)) = R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)}</pre>
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      int maxvals = Math.max(1, Math.max(p1.distinctValues(fldname1),
                                         p2.distinctValues(fldname2)));
      return (p1.recordsOutput() * p2.recordsOutput()) / maxvals;
   }

   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   public Schema schema() {
      return sch;
   }

   /**
    * The partition that a join value hashes to.
    * The hash code is scrambled first so that the in-memory hash table
    * (which also uses hashCode) does not see only the values whose
    * hash codes share the same remainder mod k.
    */
   static int partitionOf(Constant val, int k) {
      int h = val.hashCode() * 0x9E3779B9;
      return Math.floorMod(h ^ (h >>> 16), k);
   }

   private List<TempTable> partition(Scan src, Schema srcsch, String fldname, int k) {
      List<TempTable> parts = new ArrayList<>();
      List<UpdateScan> dests = new ArrayList<>();
      for (int i=0; i<k; i++) {
         TempTable t = new TempTable(tx, srcsch);
         parts.add(t);
         dests.add(t.open());
      }
      src.beforeFirst();
      while (src.next()) {
         UpdateScan dest = dests.get(partitionOf(src.getVal(fldname), k));
         dest.insert();
         for (String f : srcsch.fields())
            dest.setVal(f, src.getVal(f));
      }
      src.close();
      for (UpdateScan dest : dests)
         dest.close();
      return parts;
   }

   private static int materializedSize(Transaction tx, Plan p) {
      return new MaterializePlan(tx, p).blocksAccessed();
   }

   public List<Plan> children() {
      return List.of(p1, p2);
   }

   public String toString() {
      return "HashJoin(" + fldname1 + " = " + fldname2 + ")";
   }
}
