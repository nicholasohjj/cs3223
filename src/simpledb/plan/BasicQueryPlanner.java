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
      if (data instanceof SetQueryData)
         return SetQueryPlanner.createPlan((SetQueryData) data, this, tx);

      //Step 0: Resolve every field name to its qualified "alias.field" form
      QueryResolver resolver = new QueryResolver(data, mdm, tx);
      data = resolver.query();

      //Step 1: Create a plan for each mentioned table or view,
      //        whose fields are qualified by the table's alias.
     List<Plan> plans = new ArrayList<>();
      Iterator<String> aliases = data.aliases().iterator();
      for (String tblname : data.tables()) {
         String alias = aliases.next();
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) { // Recursively plan the view.
            Parser parser = new Parser(viewdef);
            QueryData viewdata = parser.query();
            plans.add(RenamePlan.qualify(createPlan(viewdata, tx), alias));
         }
         else
            plans.add(new TablePlan(tx, tblname, alias, mdm));
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

      //Step 8: Give the output fields the names the query used
      return new RenamePlan(p, resolver.outputNames());
   }
}
