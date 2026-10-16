package simpledb.metadata;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.Constant;

/**
 * The statistics manager is responsible for
 * keeping statistical information about each table.
 * The manager does not store this information in the database.
 * Instead, it calculates this information on system startup,
 * and periodically refreshes it.
 * @author Edward Sciore
 */
class StatMgr {
   private TableMgr tblMgr;
   private Map<String,StatInfo> tablestats;
   private int numcalls;
   private Map<String,Integer> modifications = new HashMap<>();
   
   /**
    * Create the statistics manager.
    * The initial statistics are calculated by
    * traversing the entire database.
    * @param tx the startup transaction
    */
   public StatMgr(TableMgr tblMgr, Transaction tx) {
      this.tblMgr = tblMgr;
      refreshStatistics(tx);
   }
   
   /**
    * Return the statistical information about the specified table.
    * @param tblname the name of the table
    * @param layout the table's layout
    * @param tx the calling transaction
    * @return the statistical information about the table
    */
   public synchronized StatInfo getStatInfo(String tblname, 
                              Layout layout, Transaction tx) {
      numcalls++;
      if (numcalls > 100)
         refreshStatistics(tx);
      StatInfo si = tablestats.get(tblname);
      // recompute the statistics once the records changed since they were
      // gathered exceed a tenth of the table (or any change, for a tiny table)
      int changed = modifications.getOrDefault(tblname, 0);
      if (si != null && changed > Math.max(0, si.recordsOutput() / 10))
         si = null;
      if (si == null) {
         si = calcTableStats(tblname, layout, tx);
         tablestats.put(tblname, si);
         modifications.remove(tblname);
      }
      return si;
   }

   /**
    * Records that the specified number of records of the table were
    * inserted, deleted or modified, so that its statistics can be
    * recomputed when they become out of date.
    * @param tblname the name of the table
    * @param count the number of records changed
    */
   public synchronized void recordModified(String tblname, int count) {
      modifications.merge(tblname, count, Integer::sum);
   }
   
   private synchronized void refreshStatistics(Transaction tx) {
      tablestats = new HashMap<String,StatInfo>();
      modifications.clear();
      numcalls = 0;
      Layout tcatlayout = tblMgr.getLayout("tblcat", tx);
      TableScan tcat = new TableScan(tx, "tblcat", tcatlayout);
      while(tcat.next()) {
         String tblname = tcat.getString("tblname");
         Layout layout = tblMgr.getLayout(tblname, tx);
         StatInfo si = calcTableStats(tblname, layout, tx);
         tablestats.put(tblname, si);
      }
      tcat.close();
   }
   
   private synchronized StatInfo calcTableStats(String tblname, 
                              Layout layout, Transaction tx) {
      int numRecs = 0;
      Map<String,Set<Constant>> values = new HashMap<>();
      for (String fldname : layout.schema().fields())
         values.put(fldname, new HashSet<>());
      TableScan ts = new TableScan(tx, tblname, layout);
      while (ts.next()) {
         numRecs++;
         for (String fldname : layout.schema().fields())
            values.get(fldname).add(ts.getVal(fldname));
      }
      ts.close();
      Map<String,Integer> distinct = new HashMap<>();
      for (String fldname : values.keySet())
         distinct.put(fldname, values.get(fldname).size());
      int numblocks = tx.approximateSize(tblname + ".tbl");
      return new StatInfo(numblocks, numRecs, distinct);
   }
}
