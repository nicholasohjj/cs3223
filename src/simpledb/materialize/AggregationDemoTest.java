package simpledb.materialize;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;

/**
 * A manual, end-to-end demonstration of GROUP BY and the
 * count/sum/avg/min/max aggregation functions, both grouped
 * and ungrouped, run through the SQL parser and planner.
 * @author Edward Sciore
 */
public class AggregationDemoTest {
   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("aggregationdemotest");
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      planner.executeUpdate("create table emp(id int, dept varchar(9), sal int)", tx);
      int[][] rows = {
         {1, 0, 5000},  // eng
         {2, 0, 7000},  // eng
         {3, 0, 9000},  // eng
         {4, 1, 4000},  // sales
         {5, 1, 6000},  // sales
         {6, 2, 8000},  // hr
      };
      String[] depts = {"eng", "sales", "hr"};
      for (int[] r : rows) {
         String cmd = "insert into emp(id, dept, sal) values(" + r[0] + ", '" +
                 depts[r[1]] + "', " + r[2] + ")";
         planner.executeUpdate(cmd, tx);
      }

      System.out.println("-- with GROUP BY --");
      runQuery(planner, tx,
         "select dept, count(id), sum(sal), avg(sal), min(sal), max(sal) from emp group by dept",
         "dept", "countofid", "sumofsal", "avgofsal", "minofsal", "maxofsal");

      System.out.println("-- without GROUP BY (whole table is one group) --");
      runQuery(planner, tx,
         "select count(id), sum(sal), avg(sal), min(sal), max(sal) from emp",
         "countofid", "sumofsal", "avgofsal", "minofsal", "maxofsal");

      System.out.println("-- GROUP BY with no aggregate function (distinct depts) --");
      runQuery(planner, tx, "select dept from emp group by dept", "dept");

      System.out.println("-- sort-merge join feeding into a group by/aggregate --");
      planner.executeUpdate("create table dept(dname varchar(9), budget int)", tx);
      for (String d : depts)
         planner.executeUpdate("insert into dept(dname, budget) values('" + d + "', 100000)", tx);
      runQuery(planner, tx,
         "select dept, sum(sal) from emp, dept where dept = dname group by dept",
         "dept", "sumofsal");

      tx.commit();
   }

   private static void runQuery(Planner planner, Transaction tx, String qry, String... fields) {
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      while (s.next()) {
         StringBuilder line = new StringBuilder();
         for (String fld : fields)
            line.append(fld).append("=").append(s.getVal(fld)).append("  ");
         System.out.println(line);
      }
      s.close();
   }
}
