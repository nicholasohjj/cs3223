package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The Scan class for the <i>hash join</i> operator.
 * The two inputs have already been partitioned on their join
 * fields.  For each partition number in turn, the build partition
 * is loaded into an in-memory hash table keyed on its join value,
 * and the probe partition is scanned, returning each probe record
 * paired with every build record having the same join value.
 */
public class HashJoinScan implements Scan {
   private List<TempTable> probeparts, buildparts;
   private String probefld, buildfld;
   private Schema probesch, buildsch;
   private int part;
   private Scan probe;
   private Map<Constant,List<Map<String,Constant>>> table;
   private List<Map<String,Constant>> matches;
   private int matchpos;
   private Map<String,Constant> buildrec;

   /**
    * Create a hash join scan over the specified partitions.
    * @param probeparts the partitions of the probe input
    * @param probefld the join field of the probe input
    * @param buildparts the partitions of the build input
    * @param buildfld the join field of the build input
    * @param buildsch the schema of the build input
    */
   public HashJoinScan(List<TempTable> probeparts, String probefld,
                       List<TempTable> buildparts, String buildfld, Schema buildsch) {
      this.probeparts = probeparts;
      this.probefld = probefld;
      this.buildparts = buildparts;
      this.buildfld = buildfld;
      this.buildsch = buildsch;
      probesch = probeparts.get(0).getLayout().schema();
      beforeFirst();
   }

   /**
    * Position the scan before the first record of the first partition.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      closeProbe();
      part = -1;
      matches = null;
   }

   /**
    * Move to the next joined record: the next build record that
    * matches the current probe record, or else the first match of
    * the next probe record, moving on to the next partition
    * when the current one is exhausted.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (true) {
         if (matches != null && matchpos < matches.size()) {
            buildrec = matches.get(matchpos++);
            return true;
         }
         if (probe != null && probe.next()) {
            matches = table.get(probe.getVal(probefld));
            matchpos = 0;
            continue;
         }
         closeProbe();
         matches = null;
         part++;
         if (part >= probeparts.size())
            return false;
         table = loadPartition(buildparts.get(part));
         if (!table.isEmpty())  // otherwise nothing in this partition can match
            probe = probeparts.get(part).open();
      }
   }

   public void close() {
      closeProbe();
   }

   public Constant getVal(String fldname) {
      if (probesch.hasField(fldname))
         return probe.getVal(fldname);
      else
         return buildrec.get(fldname);
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public boolean hasField(String fldname) {
      return probesch.hasField(fldname) || buildsch.hasField(fldname);
   }

   private Map<Constant,List<Map<String,Constant>>> loadPartition(TempTable t) {
      Map<Constant,List<Map<String,Constant>>> result = new HashMap<>();
      Scan s = t.open();
      while (s.next()) {
         Map<String,Constant> rec = new HashMap<>();
         for (String f : buildsch.fields())
            rec.put(f, s.getVal(f));
         result.computeIfAbsent(rec.get(buildfld), v -> new ArrayList<>()).add(rec);
      }
      s.close();
      return result;
   }

   private void closeProbe() {
      if (probe != null)
         probe.close();
      probe = null;
   }
}
