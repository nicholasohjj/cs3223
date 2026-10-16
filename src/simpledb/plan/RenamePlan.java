package simpledb.plan;

import java.util.*;
import simpledb.query.RenameScan;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The Plan class for the <i>rename</i> operator, which gives
 * fields of its input new names, leaving the records unchanged.
 * It is used to qualify the output fields of a view
 * (e.g. "sid" becomes "v.sid"), and to give a query's
 * output fields the names the query used for them.
 */
public class RenamePlan implements Plan {
   private Plan p;
   private Map<String,String> newnames;   // old name -> new name
   private Map<String,String> oldnames = new HashMap<>();
   private Schema schema = new Schema();

   /**
    * Creates a rename node in the query tree.
    * @param p the subquery
    * @param newnames maps an input field to its new name; fields
    *                 not in the map keep their names
    */
   public RenamePlan(Plan p, Map<String,String> newnames) {
      this.p = p;
      this.newnames = newnames;
      Schema sch = p.schema();
      for (String fldname : sch.fields()) {
         String newname = newnames.getOrDefault(fldname, fldname);
         oldnames.put(newname, fldname);
         schema.addField(newname, sch.type(fldname), sch.length(fldname));
      }
   }

   /**
    * Creates a rename node that prefixes every field of
    * the subquery with "alias.".
    * @param p the subquery
    * @param alias the qualifier for the fields
    * @return the rename plan
    */
   public static RenamePlan qualify(Plan p, String alias) {
      Map<String,String> newnames = new LinkedHashMap<>();
      for (String fldname : p.schema().fields())
         newnames.put(fldname, alias + "." + fldname);
      return new RenamePlan(p, newnames);
   }

   public Scan open() {
      return new RenameScan(p.open(), oldnames);
   }

   public int blocksAccessed() {
      return p.blocksAccessed();
   }

   public int recordsOutput() {
      return p.recordsOutput();
   }

   public int distinctValues(String fldname) {
      return p.distinctValues(oldnames.getOrDefault(fldname, fldname));
   }

   public Schema schema() {
      return schema;
   }

   public List<Plan> children() {
      return List.of(p);
   }

   public String toString() {
      List<String> renamed = new ArrayList<>();
      for (String fldname : p.schema().fields()) {
         String newname = newnames.getOrDefault(fldname, fldname);
         if (!newname.equals(fldname))
            renamed.add(fldname + " as " + newname);
      }
      return "Rename(" + String.join(", ", renamed) + ")";
   }
}
