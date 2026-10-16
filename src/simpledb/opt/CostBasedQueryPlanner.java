package simpledb.opt;

import java.util.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.plan.*;
import simpledb.tx.Transaction;

/**
 * A cost-based query planner, in the style of System R (Selinger).
 * It considers every left-deep join order, by dynamic programming over
 * the subsets of the from clause's tables: the cheapest plan for each
 * subset is built from the cheapest plan for the subset without one of
 * its tables, joined with that table.  For each step, every applicable
 * method is considered (index, hash, merge and nested-loops joins; and,
 * for a single table, a scan or an index select), and the cheapest is
 * kept, according to CostModel.  A table is joined by a product only
 * if no part of the predicate connects it to the rest of the subset.
 * <p>
 * Planning takes time exponential in the number of tables, so a query
 * with more than MAX_TABLES tables is planned by HeuristicQueryPlanner.
 * Grouping, sorting, projection and distinct are then added as by
 * HeuristicQueryPlanner.
 */
public class CostBasedQueryPlanner implements QueryPlanner {
   public static final int MAX_TABLES = 10;
   private MetadataMgr mdm;

   public CostBasedQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }

   public Plan createPlan(QueryData data, Transaction tx) {
      if (data instanceof SetQueryData)
         return SetQueryPlanner.createPlan((SetQueryData) data, this, tx);
      if (data.tables().size() > MAX_TABLES)
         return new HeuristicQueryPlanner(mdm).createPlan(data, tx);

      QueryResolver resolver = new QueryResolver(data, mdm, tx);
      data = resolver.query();
      List<TablePlanner> tableplanners = HeuristicQueryPlanner.tablePlanners(data, this, mdm, tx);
      CostModel model = new CostModel(tx);

      // best[s] is the cheapest plan joining the tables in the set s (a bit mask)
      int n = tableplanners.size();
      Plan[] best = new Plan[1 << n];
      for (int i=0; i<n; i++)
         best[1 << i] = cheapest(tableplanners.get(i).selectCandidates(), model);
      for (int set=1; set < (1 << n); set++) {
         if (Integer.bitCount(set) < 2)
            continue;
         List<Plan> candidates = new ArrayList<>();
         for (int i=0; i<n; i++) {
            if ((set & (1 << i)) == 0)
               continue;
            Plan rest = best[set & ~(1 << i)];   // already computed, since it is a smaller number
            TablePlanner tp = tableplanners.get(i);
            List<Plan> joins = tp.joinCandidates(rest);
            if (joins.isEmpty())   // nothing connects table i to the rest
               candidates.add(tp.makeProductPlan(rest));
            else
               candidates.addAll(joins);
         }
         best[set] = cheapest(candidates, model);
      }
      return HeuristicQueryPlanner.addFinalOperators(best[(1 << n) - 1], data, resolver, tx);
   }

   private static Plan cheapest(List<Plan> candidates, CostModel model) {
      Plan best = null;
      long bestcost = Long.MAX_VALUE;
      for (Plan p : candidates) {
         long c = model.cost(p);
         if (best == null || c < bestcost) {
            best = p;
            bestcost = c;
         }
      }
      return best;
   }
}
