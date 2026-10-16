package simpledb.opt;

import java.util.Map;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.materialize.HashJoinPlan;
import simpledb.materialize.MergeJoinPlan;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 * @author Edward Sciore
 */
class TablePlanner {
   private Plan myplan;
   private Predicate mypred;
   private Schema myschema;
   private Map<String,IndexInfo> indexes;
   private Transaction tx;
   
   /**
    * Creates a new table planner.
    * The specified predicate applies to the entire query.
    * The table planner is responsible for determining
    * which portion of the predicate is useful to the table,
    * and when indexes are useful.
    * @param tblname the name of the table
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, mdm);
      myschema = myplan.schema();
      indexes  = mdm.getIndexInfo(tblname, tx);
   }

   /**
    * Creates a table planner for an already-planned subquery,
    * such as a view.  A subquery has no indexes, so it is
    * joined using a hash, merge or nested-loops join.
    * @param subplan the plan for the subquery
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(Plan subplan, Predicate mypred, Transaction tx) {
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = subplan;
      myschema = myplan.schema();
      indexes  = Map.of();
   }
   
   /**
    * Constructs a select plan for the table.
    * The plan will use an indexselect, if possible.
    * @return a select plan for the table.
    */
   public Plan makeSelectPlan() {
      Plan p = makeIndexSelect();
      if (p == null)
         p = myplan;
      return addSelectPred(p);
   }
   
   /**
    * Constructs a join plan of the specified plan
    * and the table.  The plan will use an indexjoin, if possible.
    * (Which means that if an indexselect is also possible,
    * the indexjoin operator takes precedence.)
    * The method returns null if no join is possible.
    * @param current the specified plan
    * @return a join plan of the plan and this table
    */
   public Plan makeJoinPlan(Plan current) {
      Schema currsch = current.schema();
      Predicate joinpred = mypred.joinSubPred(myschema, currsch);
      if (joinpred == null)
         return null;
      // Prefer an index join when this table has an index on an
      // equality join field.  Otherwise, for an equality term, use a
      // hash join if it needs only one partitioning pass, else a merge
      // join; and finally fall back to a nested-loops join.
      Plan p = makeIndexJoin(current, currsch);
      if (p == null)
         p = makeHashJoin(current, currsch);
      if (p == null)
         p = makeMergeJoin(current, currsch);
      if (p == null)
         p = makeNestedLoopsJoin(current, currsch);
      return p;
   }
   
   /**
    * Constructs a product plan of the specified plan and
    * this table.
    * @param current the specified plan
    * @return a product plan of the specified plan and this table
    */
   public Plan makeProductPlan(Plan current) {
      Plan p = addSelectPred(myplan);
      return new MultibufferProductPlan(tx, current, p);
   }
   
   private Plan makeIndexSelect() {
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null) {
            IndexInfo ii = indexes.get(fldname);
            return new IndexSelectPlan(myplan, ii, val);
         }
      }
      return null;
   }
   
   private Plan makeIndexJoin(Plan current, Schema currsch) {
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            IndexInfo ii = indexes.get(fldname);
            Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
            p = addSelectPred(p);
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }

   private Plan makeHashJoin(Plan current, Schema currsch) {
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan inner = addSelectPred(myplan);
            if (!HashJoinPlan.fits(tx, current, inner))
               return null;
            Plan p = new HashJoinPlan(tx, current, inner, outerfield, fldname);
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }

   private Plan makeMergeJoin(Plan current, Schema currsch) {
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan p = new MergeJoinPlan(tx, current, addSelectPred(myplan), outerfield, fldname);
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }

   /**
    * Constructs a tuple-at-a-time nested-loops join, which evaluates
    * the join predicate inside the join loop, so it handles any
    * comparison operator (not just equality).
    */
   private Plan makeNestedLoopsJoin(Plan current, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      return new NestedLoopsJoinPlan(current, addSelectPred(myplan), joinpred);
   }
   
   private Plan addSelectPred(Plan p) {
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         return new SelectPlan(p, selectpred);
      else
         return p;
   }
   
   private Plan addJoinPred(Plan p, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      if (joinpred != null)
         return new SelectPlan(p, joinpred);
      else
         return p;
   }
}
