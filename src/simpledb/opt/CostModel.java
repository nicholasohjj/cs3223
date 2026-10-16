package simpledb.opt;

import java.util.List;
import simpledb.index.planner.*;
import simpledb.materialize.*;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.plan.*;
import simpledb.tx.Transaction;

/**
 * Estimates the total number of block accesses needed to evaluate
 * a plan, including one-time work such as sorting and partitioning.
 * (A plan's blocksAccessed() is the cost of scanning its output once,
 * which leaves such work out for some operators, so it cannot be
 * used to compare different join methods.)
 * <p>
 * With B(p) the number of blocks in the output of p, R(p) its number
 * of records, and M the number of available buffers:
 * <ul>
 * <li>a table scan costs its number of blocks, and an index select
 *     the cost of its index search and its matching records;
 * <li>select, project and rename add nothing to their input;
 * <li>a nested-loops join or product of p1 and p2 costs
 *     cost(p1) + R(p1) * cost(p2), since p2 is rescanned per record;
 * <li>an index join adds an index search (and the matching records)
 *     for each record of its outer input;
 * <li>a hash join reads its inputs, writes their partitions,
 *     and reads them back: cost(p1) + cost(p2) + 2(B(p1) + B(p2));
 * <li>a sort reads its input, writes runs of M-1 blocks, reads and
 *     writes the data once per merge pass, and reads it in the final
 *     merge; a merge join costs the sorts of its two inputs.
 * </ul>
 */
public class CostModel {
   private Transaction tx;
   private int buffers;

   public CostModel(Transaction tx) {
      this.tx = tx;
      this.buffers = tx.availableBuffs();
   }

   /**
    * Returns the estimated cost of evaluating the plan, in block accesses.
    * @param p the plan
    * @return the estimated number of block accesses
    */
   public long cost(Plan p) {
      List<Plan> in = p.children();
      if (p instanceof TablePlan || p instanceof IndexSelectPlan)
         return p.blocksAccessed();
      if (p instanceof SelectPlan || p instanceof ProjectPlan || p instanceof RenamePlan)
         return cost(in.get(0));
      if (p instanceof NestedLoopsJoinPlan || p instanceof ProductPlan)
         return add(cost(in.get(0)), mul(Math.max(1, in.get(0).recordsOutput()), cost(in.get(1))));
      if (p instanceof IndexJoinPlan)
         return add(cost(in.get(0)), p.blocksAccessed() - in.get(0).blocksAccessed());
      if (p instanceof HashJoinPlan)
         return add(add(cost(in.get(0)), cost(in.get(1))),
                    2L * (blocks(in.get(0)) + blocks(in.get(1))));
      if (p instanceof MergeJoinPlan)   // its inputs are the two sorts
         return add(cost(in.get(0)), cost(in.get(1)));
      if (p instanceof SortPlan)
         return add(cost(in.get(0)), sortCost(blocks(in.get(0))));
      if (p instanceof MaterializePlan)
         return add(cost(in.get(0)), 2L * blocks(in.get(0)));
      if (p instanceof MultibufferProductPlan) {
         // the left input is materialized, then scanned once per chunk of the right
         Plan lhs = in.get(0), rhs = in.get(1);
         return add(add(cost(lhs), cost(rhs)), p.blocksAccessed() - rhs.blocksAccessed());
      }
      long total = p.blocksAccessed();
      for (Plan child : in)
         total = add(total, cost(child));
      return total;
   }

   // the cost of sorting B blocks, as SortPlan does it (not counting reading the input)
   private long sortCost(long b) {
      int k = Math.max(2, buffers - 1);
      int finalruns = Math.max(2, buffers / 2);
      long runs = (b + k - 1) / k;
      int passes = 0;
      while (runs > finalruns) {
         runs = (runs + k - 1) / k;
         passes++;
      }
      return b + 2 * b * passes + b;   // write runs, merge passes, final merge
   }

   private long blocks(Plan p) {
      return Math.max(1, new MaterializePlan(tx, p).blocksAccessed());
   }

   private static long add(long a, long b) {
      long r = a + b;
      return (r < 0) ? Long.MAX_VALUE : r;
   }

   private static long mul(long a, long b) {
      if (a == 0 || b == 0)
         return 0;
      return (a > Long.MAX_VALUE / b) ? Long.MAX_VALUE : a * b;
   }
}
