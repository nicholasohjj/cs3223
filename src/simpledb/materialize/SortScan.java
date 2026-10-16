package simpledb.materialize;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;

/**
 * The Scan class for the <i>sort</i> operator.
 * It merges any number of sorted runs on the fly:
 * each call to next moves to the smallest current
 * record among the runs.
 * @author Edward Sciore
 */
public class SortScan implements Scan {
   private List<UpdateScan> scans = new ArrayList<>();
   private boolean[] hasmore;
   private int current = -1;
   private RecordComparator comp;
   private Schema sch;
   private List<RID> savedposition;
   private boolean[] savedhasmore;
   private int savedcurrent;

   /**
    * Create a sort scan, given a non-empty list of sorted runs.
    * @param runs the list of runs
    * @param comp the record comparator
    */
   public SortScan(List<TempTable> runs, RecordComparator comp) {
      this.comp = comp;
      sch = runs.get(0).getLayout().schema();
      for (TempTable run : runs)
         scans.add(run.open());
      hasmore = new boolean[scans.size()];
      beforeFirst();
   }

   /**
    * Position the scan before the first record in sorted order.
    * Internally, it moves to the first record of each run.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      current = -1;
      for (int i=0; i<scans.size(); i++) {
         scans.get(i).beforeFirst();
         hasmore[i] = scans.get(i).next();
      }
   }

   /**
    * Move to the next record in sorted order.
    * First, the current run is moved to its next record.
    * Then the smallest current record among the runs that
    * still have records becomes the current record.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      if (current >= 0)
         hasmore[current] = scans.get(current).next();
      current = -1;
      for (int i=0; i<scans.size(); i++)
         if (hasmore[i] && (current < 0 || comp.compare(scans.get(i), scans.get(current)) < 0))
            current = i;
      return current >= 0;
   }

   /**
    * Close all of the runs.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      for (UpdateScan s : scans)
         s.close();
   }

   /**
    * Get the value of the specified field
    * of the current record.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      return scans.get(current).getVal(fldname);
   }

   /**
    * Get the integer value of the specified field
    * of the current record.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      return scans.get(current).getInt(fldname);
   }

   /**
    * Get the string value of the specified field
    * of the current record.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      return scans.get(current).getString(fldname);
   }

   /**
    * Return true if the specified field is in the sorted records.
    * This uses the schema, so it is valid even before the
    * first call to next.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return sch.hasField(fldname);
   }

   /**
    * Save the position of the current record,
    * so that it can be restored at a later time.
    * The position of every run is saved, so that the
    * merge resumes exactly where it was.
    */
   public void savePosition() {
      savedposition = new ArrayList<>();
      for (UpdateScan s : scans)
         savedposition.add(s.getRid());
      savedhasmore = hasmore.clone();
      savedcurrent = current;
   }

   /**
    * Move the scan to its previously-saved position.
    */
   public void restorePosition() {
      for (int i=0; i<scans.size(); i++)
         scans.get(i).moveToRid(savedposition.get(i));
      hasmore = savedhasmore.clone();
      current = savedcurrent;
   }
}
