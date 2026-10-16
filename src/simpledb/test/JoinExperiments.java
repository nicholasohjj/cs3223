package simpledb.test;

import java.io.File;
import java.util.*;
import simpledb.file.FileMgr;
import simpledb.opt.JoinMethods;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * The join experiments for the SimpleDB+ report, on the large
 * student database (built first by CreateLargeStudentDB if missing).
 * <ul>
 * <li>Experiment 1: a 2-table join (student, enroll), run with each
 *     join algorithm in turn: nested loops, index, sort-merge, hash.
 * <li>Experiment 2: a 4-table join (student, enroll, section, course),
 *     run with plans obtained by restricting the join methods the
 *     optimizer may use; and with the optimizer's own choice.
 * </ul>
 * Each run is timed as opening the plan and reading every result
 * record (planning is not included).  Every configuration is run once
 * to warm up, then timed RUNS times; the median is reported, with the
 * blocks read and written in the last timed run, and the plan.
 * <p>
 * Usage: JoinExperiments [dbname [students]].  The optimizer is the
 * one SimpleDB is configured with (-Dsimpledb.planner=cost for the
 * cost-based one).
 */
public class JoinExperiments {
   private static final int RUNS = 3;

   public static void main(String[] args) {
      String dbname = (args.length > 0) ? args[0] : "studentdb_large";
      int students = (args.length > 1) ? Integer.parseInt(args[1]) : 3000;
      if (!new File(dbname).exists())
         CreateLargeStudentDB.create(dbname, students);
      SimpleDB db = new SimpleDB(dbname);

      String q2 = "select sname, grade from student, enroll where sid = studentid";
      System.out.println("\n=== Experiment 1: " + q2);
      run(db, q2, "nested", "nested loops");
      run(db, q2, "index", "index join");
      run(db, q2, "merge", "sort-merge join");
      run(db, q2, "hash", "hash join");

      String q4 = "select sname, title, grade from student, enroll, section, course "
            + "where sid = studentid and sectionid = sectid and courseid = cid";
      System.out.println("\n=== Experiment 2: " + q4);
      run(db, q4, "nested", "plan A: nested loops only");
      run(db, q4, "merge", "plan B: sort-merge only");
      run(db, q4, "hash", "plan C: hash only");
      run(db, q4, "index,nested", "plan D: index where possible, else nested loops");
      run(db, q4, "", "optimizer's choice (all methods)");
   }

   private static void run(SimpleDB db, String qry, String methods, String label) {
      JoinMethods.allow(methods.isEmpty() ? new String[0] : methods.split(","));
      FileMgr fm = db.fileMgr();
      Transaction tx = db.newTx();
      Plan p = db.planner().createQueryPlan(qry, tx);
      count(p);   // warm-up
      long[] ms = new long[RUNS];
      long rows = 0, reads = 0, writes = 0;
      for (int i=0; i<RUNS; i++) {
         long r0 = fm.blocksRead(), w0 = fm.blocksWritten(), t0 = System.nanoTime();
         rows = count(p);
         ms[i] = (System.nanoTime() - t0) / 1_000_000;
         reads = fm.blocksRead() - r0;
         writes = fm.blocksWritten() - w0;
      }
      tx.commit();
      Arrays.sort(ms);
      System.out.printf("%n--- %s [join methods: %s]%n", label, JoinMethods.describe());
      System.out.printf("median %d ms (runs: %s), %d rows, %d blocks read, %d blocks written%n",
            ms[RUNS / 2], Arrays.toString(ms), rows, reads, writes);
      System.out.print(Plans.explain(p));
   }

   private static long count(Plan p) {
      Scan s = p.open();
      long n = 0;
      while (s.next())
         n++;
      s.close();
      return n;
   }
}
