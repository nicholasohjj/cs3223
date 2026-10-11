package simpledb.plan;

import java.util.List;

/**
 * Utility for displaying a query plan as an indented tree, e.g.
 * <pre>
 * Project(sname, dname)  [~9 rows, 4 blocks]
 * +-- HashJoin(majorid = did)  [~9 rows, 4 blocks]
 *     +-- TableScan(student)  [~9 rows, 1 blocks]
 *     +-- TableScan(dept)  [~3 rows, 1 blocks]
 * </pre>
 * Each node shows its operator (from the plan's toString())
 * and the planner's estimates of its output size and cost.
 */
public class Plans {
   private Plans() {}

   /**
    * Returns the plan tree rooted at the specified plan.
    * @param p the root of the plan
    * @return a multi-line description of the plan
    */
   public static String explain(Plan p) {
      StringBuilder result = new StringBuilder();
      explain(p, "", "", result);
      return result.toString();
   }

   private static void explain(Plan p, String firstPrefix, String restPrefix, StringBuilder result) {
      result.append(firstPrefix).append(p).append("  ").append(estimates(p)).append('\n');
      List<Plan> children = p.children();
      for (int i=0; i<children.size(); i++) {
         boolean last = (i == children.size() - 1);
         explain(children.get(i), restPrefix + "+-- ",
                 restPrefix + (last ? "    " : "|   "), result);
      }
   }

   private static String estimates(Plan p) {
      try {
         return "[~" + p.recordsOutput() + " rows, " + p.blocksAccessed() + " blocks]";
      }
      catch (RuntimeException e) {  // an estimate should never stop the plan being shown
         return "";
      }
   }
}
