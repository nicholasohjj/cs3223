package simpledb.plan;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.query.Scan;
import simpledb.record.*;

/** The Plan class corresponding to a table.
  * @author Edward Sciore
  */
public class TablePlan implements Plan {
   private String tblname;
   private Transaction tx;
   private Layout layout;
   private StatInfo si;
   private String alias = null;
   
   /**
    * Creates a leaf node in the query tree corresponding
    * to the specified table.
    * @param tblname the name of the table
    * @param tx the calling transaction
    */
   public TablePlan(Transaction tx, String tblname, MetadataMgr md) {
      this.tblname = tblname;
      this.tx = tx;
      layout = md.getLayout(tblname, tx);
      si = md.getStatInfo(tblname, layout, tx);
   }

   /**
    * Creates a leaf node in the query tree corresponding
    * to the specified table, whose fields are known by
    * qualified names: field f of the table is called "alias.f".
    * The records are read through a copy of the table's layout
    * with the renamed fields (at the same offsets), so the scan
    * is an ordinary table scan, and indexes work as usual.
    * @param tblname the name of the table
    * @param alias the name the table is known by in the query
    * @param tx the calling transaction
    */
   public TablePlan(Transaction tx, String tblname, String alias, MetadataMgr md) {
      this(tx, tblname, md);
      this.alias = alias;
      Schema qualified = new Schema();
      Map<String,Integer> offsets = new HashMap<>();
      Schema sch = layout.schema();
      for (String fldname : sch.fields()) {
         String qname = alias + "." + fldname;
         qualified.addField(qname, sch.type(fldname), sch.length(fldname));
         offsets.put(qname, layout.offset(fldname));
      }
      layout = new Layout(qualified, offsets, layout.slotSize());
   }
   
   /**
    * Creates a table scan for this query.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      return new TableScan(tx, tblname, layout);
   }
   
   /**
    * Estimates the number of block accesses for the table,
    * which is obtainable from the statistics manager.
    * @see simpledb.plan.Plan#blocksAccessed()
    */ 
   public int blocksAccessed() {
      return si.blocksAccessed();
   }
   
   /**
    * Estimates the number of records in the table,
    * which is obtainable from the statistics manager.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      return si.recordsOutput();
   }
   
   /**
    * Estimates the number of distinct field values in the table,
    * which is obtainable from the statistics manager.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      // the statistics know the fields by their plain names
      if (alias != null && fldname.startsWith(alias + "."))
         fldname = fldname.substring(alias.length() + 1);
      return si.distinctValues(fldname);
   }
   
   /**
    * Determines the schema of the table,
    * which is obtainable from the catalog manager.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return layout.schema();
   }

   public String toString() {
      return "TableScan(" + tblname + (alias == null || alias.equals(tblname) ? "" : " " + alias) + ")";
   }
}
