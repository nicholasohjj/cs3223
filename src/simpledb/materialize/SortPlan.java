package simpledb.materialize;

import java.util.*;

import simpledb.parse.OrderByElement;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.plan.Plan;
import simpledb.query.*;

/**
 * The Plan class for the <i>sort</i> operator.
 * @author Edward Sciore
 */
public class SortPlan implements Plan {
   private Transaction tx;
   private Plan p;
   private Schema sch;
   private RecordComparator comp;
   private List<OrderByElement> sortfields;
   
   /**
    * Create a sort plan for the specified query.
    * @param p the plan for the underlying query
    * @param sortfields the fields to sort by
    * @param tx the calling transaction
    */
   public SortPlan(Transaction tx, Plan p, List<OrderByElement> sortfields) {
      this.tx = tx;
      this.p = p;
      sch = p.schema();
      this.sortfields = sortfields;
      comp = new RecordComparator(sortfields);
   }

   public static SortPlan forFields(Transaction tx, Plan p, List<String> sortfields) {
      return new SortPlan(tx, p, toOrderByElements(sortfields));
   }

   private static List<OrderByElement> toOrderByElements(List<String> fields) {
      List<OrderByElement> result = new ArrayList<>();
      for (String fld: fields) {
         result.add(new OrderByElement(fld, true));
      }
      return result;
   }
   
   /**
    * This method is where most of the action is.
    * The sort is a k-way external merge sort, where k is
    * determined by the buffers available when the plan is opened:
    * the input is split into runs of k blocks each, sorted in memory,
    * and runs are then merged k at a time.  Merging stops once few
    * enough runs remain for SortScan to merge them on the fly,
    * leaving buffers free for any other sort in the same query
    * (such as the other input of a merge join).
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan src = p.open();
      int avail = tx.availableBuffs();
      int k = Math.max(2, avail - 1);          // k input buffers and 1 output buffer
      int finalruns = Math.max(2, avail / 2);  // runs that SortScan keeps open
      List<TempTable> runs = splitIntoRuns(src, k);
      src.close();
      if (runs.isEmpty())  // an empty input sorts to a single empty run
         runs.add(new TempTable(tx, sch));
      while (runs.size() > finalruns)
         runs = doAMergeIteration(runs, k);
      return new SortScan(runs, comp);
   }
   
   /**
    * Return the number of blocks in the sorted table,
    * which is the same as it would be in a
    * materialized table.
    * It does <i>not</i> include the one-time cost
    * of materializing and sorting the records.
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      // does not include the one-time cost of sorting
      Plan mp = new MaterializePlan(tx, p); // not opened; just for analysis
      return mp.blocksAccessed();
   }
   
   /**
    * Return the number of records in the sorted table,
    * which is the same as in the underlying query.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      return p.recordsOutput();
   }
   
   /**
    * Return the number of distinct field values in
    * the sorted table, which is the same as in
    * the underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      return p.distinctValues(fldname);
   }
   
   /**
    * Return the schema of the sorted table, which
    * is the same as in the underlying query.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return sch;
   }
   
   /**
    * Split the input into runs: each run holds k blocks' worth of
    * records, which are read into memory, sorted, and written out.
    */
   private List<TempTable> splitIntoRuns(Scan src, int k) {
      int rpb = Math.max(1, tx.blockSize() / new Layout(sch).slotSize());
      int runsize = k * rpb;
      List<TempTable> temps = new ArrayList<>();
      List<Map<String,Constant>> records = new ArrayList<>();
      src.beforeFirst();
      while (src.next()) {
         Map<String,Constant> rec = new HashMap<>();
         for (String fldname : sch.fields())
            rec.put(fldname, src.getVal(fldname));
         records.add(rec);
         if (records.size() == runsize) {
            temps.add(writeRun(records));
            records.clear();
         }
      }
      if (!records.isEmpty())
         temps.add(writeRun(records));
      return temps;
   }

   private TempTable writeRun(List<Map<String,Constant>> records) {
      records.sort(comp::compare);
      TempTable run = new TempTable(tx, sch);
      UpdateScan dest = run.open();
      for (Map<String,Constant> rec : records) {
         dest.insert();
         for (String fldname : sch.fields())
            dest.setVal(fldname, rec.get(fldname));
      }
      dest.close();
      return run;
   }
   
   private List<TempTable> doAMergeIteration(List<TempTable> runs, int k) {
      List<TempTable> result = new ArrayList<>();
      for (int i=0; i<runs.size(); i+=k) {
         List<TempTable> group = runs.subList(i, Math.min(i + k, runs.size()));
         result.add(group.size() == 1 ? group.get(0) : mergeRuns(group));
      }
      return result;
   }
   
   /**
    * Merge the specified sorted runs into a single sorted run,
    * by repeatedly copying the smallest current record among them.
    */
   private TempTable mergeRuns(List<TempTable> group) {
      List<Scan> srcs = new ArrayList<>();
      List<Boolean> hasmore = new ArrayList<>();
      for (TempTable t : group) {
         Scan src = t.open();
         srcs.add(src);
         hasmore.add(src.next());
      }
      TempTable result = new TempTable(tx, sch);
      UpdateScan dest = result.open();
      while (true) {
         int min = -1;
         for (int i=0; i<srcs.size(); i++)
            if (hasmore.get(i) && (min < 0 || comp.compare(srcs.get(i), srcs.get(min)) < 0))
               min = i;
         if (min < 0)
            break;
         hasmore.set(min, copy(srcs.get(min), dest));
      }
      for (Scan src : srcs)
         src.close();
      dest.close();
      return result;
   }
   
   private boolean copy(Scan src, UpdateScan dest) {
      dest.insert();
      for (String fldname : sch.fields())
         dest.setVal(fldname, src.getVal(fldname));
      return src.next();
   }

   public List<Plan> children() {
      return List.of(p);
   }

   public String toString() {
      return "Sort(" + OrderByElement.listString(sortfields) + ")";
   }
}
