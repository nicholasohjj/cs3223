package simpledb.plan;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.parse.*;
import simpledb.materialize.*;

/**
 * The simplest, most naive query planner possible.
 * @author Edward Sciore
 */
public class BasicQueryPlanner implements QueryPlanner {
   private MetadataMgr mdm;
   
   public BasicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates a query plan as follows.  It first takes
    * the product of all tables and views; it then selects on the predicate;
    * and finally it projects on the field list. 
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      //Step 1: Create a plan for each mentioned table or view.
     List<Plan> plans = new ArrayList<>();
      for (String tblname : data.tables()) {
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) { // Recursively plan the view.
            Parser parser = new Parser(viewdef);
            QueryData viewdata = parser.query();
            plans.add(createPlan(viewdata, tx));
         }
         else
            plans.add(new TablePlan(tx, tblname, mdm));
      }
      
      //Step 2: Create the product of all table plans
      Plan p = plans.remove(0);
      for (Plan nextplan : plans)
         p = new ProductPlan(p, nextplan);
      
      //Step 3: Add a selection plan for the predicate
      p = new SelectPlan(p, data.pred());

      //Step 4: Group and aggregate, if requested
      if (data.isGrouped())
         p = new GroupByPlan(tx, p, data.groupByFields(), data.aggFns());

      //Step 5: Sort, if requested (a distinct query sorts in step 7 instead)
      if (!data.isDistinct() && !data.orderByList().isEmpty())
         p = new SortPlan(tx, p, data.orderByList());

      //Step 6: Project on the field names
      p = new ProjectPlan(p, data.fields());

      //Step 7: Remove duplicate rows, if requested
      if (data.isDistinct())
         p = new DistinctPlan(tx, p, data.orderByList());
      return p;
   }
}
