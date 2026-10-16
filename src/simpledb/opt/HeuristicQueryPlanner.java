package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.Parser;
import simpledb.parse.QueryData;
import simpledb.parse.SetQueryData;
import simpledb.materialize.GroupByPlan;
import simpledb.materialize.SortPlan;
import simpledb.materialize.DistinctPlan;
import simpledb.plan.*;

/**
 * A query planner that optimizes using a heuristic-based algorithm.
 * @author Edward Sciore
 */
public class HeuristicQueryPlanner implements QueryPlanner {
   private MetadataMgr mdm;
   
   public HeuristicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates an optimized left-deep query plan using the following
    * heuristics.
    * H1. Choose the smallest table (considering selection predicates)
    * to be first in the join order.
    * H2. Add the table to the join order which
    * results in the smallest output.
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      if (data instanceof SetQueryData)
         return SetQueryPlanner.createPlan((SetQueryData) data, this, tx);
      
      // Step 0:  Resolve every field name to its qualified "alias.field" form
      QueryResolver resolver = new QueryResolver(data, mdm, tx);
      data = resolver.query();

      // Step 1:  Create a TablePlanner object for each mentioned table or view
      // (they are local, so that a view can be planned recursively and
      // concurrent queries, e.g. via the network server, do not interfere)
      Collection<TablePlanner> tableplanners = tablePlanners(data, this, mdm, tx);
      
      // Step 2:  Choose the lowest-size plan to begin the join order
      Plan currentplan = getLowestSelectPlan(tableplanners);
      
      // Step 3:  Repeatedly add a plan to the join order
      while (!tableplanners.isEmpty()) {
         Plan p = getLowestJoinPlan(tableplanners, currentplan);
         if (p != null)
            currentplan = p;
         else  // no applicable join
            currentplan = getLowestProductPlan(tableplanners, currentplan);
      }
      
      // Steps 4-8
      return addFinalOperators(currentplan, data, resolver, tx);
   }

   /**
    * Creates a table planner for each table or view of the (resolved)
    * query, in order.  A view is planned recursively by qp, and its
    * fields qualified by its alias.
    */
   static List<TablePlanner> tablePlanners(QueryData data, QueryPlanner qp, MetadataMgr mdm, Transaction tx) {
      List<TablePlanner> tableplanners = new ArrayList<>();
      Iterator<String> aliases = data.aliases().iterator();
      for (String tblname : data.tables()) {
         String alias = aliases.next();
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) { // Recursively plan the view.
            Plan viewplan = qp.createPlan(new Parser(viewdef).query(), tx);
            tableplanners.add(new TablePlanner(RenamePlan.qualify(viewplan, alias), data.pred(), tx));
         }
         else
            tableplanners.add(new TablePlanner(tblname, alias, data.pred(), tx, mdm));
      }
      return tableplanners;
   }

   /**
    * Adds the operators that follow the joins: group by, sort,
    * project, distinct, and the renaming of the output fields.
    */
   static Plan addFinalOperators(Plan p, QueryData data, QueryResolver resolver, Transaction tx) {
      // Step 4.  Group and aggregate, if requested
      if (data.isGrouped())
         p = new GroupByPlan(tx, p, data.groupByFields(), data.aggFns());

      // Step 5.  Sort, if requested (a distinct query sorts in step 7 instead)
      if (!data.isDistinct() && !data.orderByList().isEmpty())
         p = new SortPlan(tx, p, data.orderByList());

      // Step 6.  Project on the field names
      p = new ProjectPlan(p, data.fields());

      // Step 7.  Remove duplicate rows, if requested
      if (data.isDistinct())
         p = new DistinctPlan(tx, p, data.orderByList());

      // Step 8.  Give the output fields the names the query used
      return new RenamePlan(p, resolver.outputNames());
   }
   
   private Plan getLowestSelectPlan(Collection<TablePlanner> tableplanners) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeSelectPlan();
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestJoinPlan(Collection<TablePlanner> tableplanners, Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeJoinPlan(current);
         if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
            besttp = tp;
            bestplan = plan;
         }
      }
      if (bestplan != null)
         tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestProductPlan(Collection<TablePlanner> tableplanners, Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeProductPlan(current);
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }

   public void setPlanner(Planner p) {
      // for use in planning views, which
      // for simplicity this code doesn't do.
   }
}
