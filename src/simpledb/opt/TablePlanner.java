package simpledb.opt;

import java.util.*;
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
    * Creates a table planner for a table whose fields are known
    * by qualified names "alias.field", as resolved by QueryResolver.
    * Each index is then keyed by the qualified name of its field.
    * @param tblname the name of the table
    * @param alias the name the table is known by in the query
    * @param mypred the query predicate, with qualified field names
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, String alias, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, alias, mdm);
      myschema = myplan.schema();
      indexes  = new HashMap<>();
      for (Map.Entry<String,IndexInfo> e : mdm.getIndexInfo(tblname, tx).entrySet())
         indexes.put(alias + "." + e.getKey(), e.getValue());
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
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null)
            return makeIndexSelect(fldname, val);
      }
      return addSelectPred(myplan);
   }

   /**
    * Returns every way of selecting from the table: a scan
    * followed by a selection, and an index select for each
    * index on a field that the predicate equates with a constant.
    * Used by the cost-based planner, which chooses between them.
    * @return the candidate select plans
    */
   public List<Plan> selectCandidates() {
      List<Plan> result = new ArrayList<>();
      result.add(addSelectPred(myplan));
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null)
            result.add(makeIndexSelect(fldname, val));
      }
      return result;
   }

   /**
    * Returns every applicable way of joining the specified plan with
    * the table: index, hash and merge joins (for an equality term),
    * and a nested-loops join.  Returns an empty list if no part of the
    * predicate joins the two.
    * Used by the cost-based planner, which chooses between them.
    * @param current the specified plan
    * @return the candidate join plans
    */
   public List<Plan> joinCandidates(Plan current) {
      Schema currsch = current.schema();
      List<Plan> result = new ArrayList<>();
      if (mypred.joinSubPred(myschema, currsch) == null)
         return result;
      for (Plan p : Arrays.asList(makeIndexJoin(current, currsch), makeHashJoin(current, currsch),
                                  makeMergeJoin(current, currsch)))
         if (p != null)
            result.add(p);
      if (result.isEmpty() || JoinMethods.isAllowed(JoinMethods.NESTED))
         result.add(makeNestedLoopsJoin(current, currsch));
      return result;
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
      // Only the join methods allowed by JoinMethods are tried; a
      // nested-loops join is the fallback when none of them applies.
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
   
   // the index select already guarantees "fldname = val", so that term is not re-checked
   private Plan makeIndexSelect(String fldname, Constant val) {
      Plan p = new IndexSelectPlan(myplan, indexes.get(fldname), val);
      return select(p, mypred.selectSubPred(myschema), t -> t.equatesFieldWith(fldname, val));
   }
   
   private Plan makeIndexJoin(Plan current, Schema currsch) {
      if (!JoinMethods.isAllowed(JoinMethods.INDEX))
         return null;
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            IndexInfo ii = indexes.get(fldname);
            Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
            p = addSelectPred(p);
            return addJoinPred(p, currsch, fldname, outerfield);
         }
      }
      return null;
   }

   private Plan makeHashJoin(Plan current, Schema currsch) {
      if (!JoinMethods.isAllowed(JoinMethods.HASH))
         return null;
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan inner = addSelectPred(myplan);
            // a hash join that needs more than one partitioning pass is
            // left to the merge join, unless merge joins are not allowed
            if (!HashJoinPlan.fits(tx, current, inner) && JoinMethods.isAllowed(JoinMethods.MERGE))
               return null;
            Plan p = new HashJoinPlan(tx, current, inner, outerfield, fldname);
            return addJoinPred(p, currsch, fldname, outerfield);
         }
      }
      return null;
   }

   private Plan makeMergeJoin(Plan current, Schema currsch) {
      if (!JoinMethods.isAllowed(JoinMethods.MERGE))
         return null;
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan p = new MergeJoinPlan(tx, current, addSelectPred(myplan), outerfield, fldname);
            return addJoinPred(p, currsch, fldname, outerfield);
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
      return select(p, mypred.selectSubPred(myschema), t -> false);
   }

   // the join operator already guarantees "f1 = f2", so that term is not re-checked
   private Plan addJoinPred(Plan p, Schema currsch, String f1, String f2) {
      return select(p, mypred.joinSubPred(currsch, myschema), t -> t.equatesFields(f1, f2));
   }

   // selects on the terms of pred that are not already handled
   private Plan select(Plan p, Predicate pred, java.util.function.Predicate<Term> handled) {
      if (pred == null)
         return p;
      pred = pred.without(handled);
      return pred.terms().isEmpty() ? p : new SelectPlan(p, pred);
   }
}
