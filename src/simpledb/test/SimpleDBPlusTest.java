package simpledb.test;

import java.io.File;
import java.util.*;
import simpledb.index.planner.IndexUpdatePlanner;
import simpledb.opt.*;
import simpledb.parse.Parser;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.plan.Plans;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * End-to-end test of the SimpleDB+ features from all labs:
 * non-equality predicates, hash and btree indexes, order by,
 * the index/merge/hash/nested-loops joins, aggregates with and
 * without group by, distinct, query plan display, views and updates;
 * and the extensions: the double type, the cost-based optimizer,
 * qualified (table.field) names, multi-way sorting, set operations
 * and query validation.
 * <p>
 * It builds a fresh copy of the student database (plus two larger
 * tables, for the merge join) in the directory "simpledbplustest",
 * deleting any previous copy, and checks each query's rows, and where
 * it matters, that the plan uses the expected operator.
 * Exits with status 1 if any check fails.
 * Run with -Dsimpledb.planner=cost to test the cost-based planner,
 * in which case only the rows are checked, not the plans.
 */
public class SimpleDBPlusTest {
   private static final String DBNAME = "simpledbplustest";
   private static int passed = 0, failed = 0;
   // the plan checks describe the heuristic planner's choices; the
   // cost-based planner (-Dsimpledb.planner=cost) may choose differently
   private static final boolean HEURISTIC = !"cost".equals(System.getProperty("simpledb.planner"));

   public static void main(String[] args) {
      deleteDirectory(new File(DBNAME));
      SimpleDB db = new SimpleDB(DBNAME);
      Planner planner = db.planner();
      Transaction tx = db.newTx();
      CreateStudentDB.createTables(planner, tx);
      createLargeTables(planner, tx);
      tx.commit();
      tx = db.newTx();

      section("Lab 1: non-equality predicates");
      check(planner, tx, "select sid from student where sid >= 7", null, "7|8|9");
      check(planner, tx, "select sname from student where sname != 'joe' and sname < 'c'", null, "amy|bob|art");
      check(planner, tx, "select sname from student where sname <> 'joe' and gradyear <= 2020 and sid > 5", null, "kim|pat");

      section("Lab 2: hash and btree indexes");
      check(planner, tx, "select sname from student where majorid = 10", "IndexSelect(majorid = 10, hash", "joe|max|lee");
      check(planner, tx, "select sname from student where sid = 4", "IndexSelect(sid = 4, btree", "sue");
      check(planner, tx, "select sname from student where majorid > 20", "!IndexSelect", "bob|art");

      section("Lab 3: order by");
      check(planner, tx, "select sname, gradyear from student order by gradyear asc, sname desc", "Sort(student.gradyear asc, student.sname desc)",
            "pat 2019|kim 2020|bob 2020|amy 2020|lee 2021|joe 2021|art 2021|sue 2022|max 2022");
      check(planner, tx, "select sname from student where majorid = 20 order by gradyear desc, sname", null, "sue|amy|kim|pat");

      section("Lab 4: join algorithms");
      check(planner, tx, "select sname, grade from student, enroll where sid = studentid order by sname, grade", "IndexJoin",
            "amy B+|joe A|joe C|kim A|sue A|sue B");
      check(planner, tx, "select b, h from bigr, bigu where a = g and b < 3 and h < 200 order by b, h", null,
            "0 0|0 89|0 178|1 1|1 90|1 179|2 2|2 91|2 180");
      check(planner, tx, "select count(b) from bigr, bigu where a = g", "MergeJoin(", "20175");
      check(planner, tx, "select sid, studentid from student, enroll where sid > studentid and sid < 3", "NestedLoopsJoin(student.sid>enroll.studentid)", "2 1|2 1");

      section("Lab 5: hash join and aggregates");
      check(planner, tx, "select title, prof from course, section where cid = courseid and prof = 'turing'", "HashJoin(",
            "db systems turing|db systems turing");
      check(planner, tx, "select count(sid), sum(gradyear), avg(gradyear), min(gradyear), max(sname) from student", null,
            "9 18186 2020.6666666666667 2019 sue");
      check(planner, tx, "select majorid, count(sid), min(sname), max(gradyear) from student group by majorid order by majorid", "GroupBy(student.majorid",
            "10 3 joe 2022|20 4 amy 2022|30 2 art 2021");
      check(planner, tx, "select count(sid) from student where sid > 100", null, "0");
      checkRejected(planner, tx, "select sname, count(sid) from student group by majorid");

      section("Lab 6: distinct and plan display");
      check(planner, tx, "select distinct majorid from student order by majorid desc", "Distinct(", "30|20|10");
      check(planner, tx, "select distinct grade from enroll order by grade", null, "A|B|B+|C");
      checkRejected(planner, tx, "select distinct sname from student order by gradyear");
      Plan shown = planner.createQueryPlan("select distinct dname from student, dept where majorid = did and gradyear > 2020", tx);
      System.out.println("  example plan:");
      for (String line : Plans.explain(shown).split("\n"))
         System.out.println("    " + line);

      section("Lab 7: everything together");
      check(planner, tx, "select dname, count(sid), min(gradyear) from student, dept where majorid = did and gradyear >= 2020 "
            + "group by dname order by countofsid desc, dname", null, "compsci 3 2021|math 3 2020|drama 2 2020");
      check(planner, tx, "select distinct dname from student, dept, enroll where majorid = did and sid = studentid and grade <> 'C' "
            + "order by dname desc", null, "math|compsci");
      check(planner, tx, "select sname, title from student, enroll, section, course where sid = studentid and sectionid = sectid "
            + "and courseid = cid and sid <= 4 order by sname, title", null,
            "amy calculus|joe calculus|joe db systems|sue calculus|sue elocution");
      check(planner, tx, "select title, count(eid), max(grade) from enroll, section, course where sectionid = sectid "
            + "and courseid = cid group by title order by title", null, "calculus 3 C|db systems 1 A|elocution 2 A");
      check(planner, tx, "select sname, count(eid) from student, enroll where sid < studentid group by sname "
            + "order by countofeid desc, sname", null, "joe 4|amy 3|max 3|bob 1|sue 1");

      // views, including one whose definition has an aggregate and a string constant
      planner.executeUpdate("create view mathview as select sid, sname from student where majorid = 20", tx);
      planner.executeUpdate("create view gradecount as select grade, count(eid) from enroll where grade != 'C' group by grade", tx);
      check(planner, tx, "select sname from mathview where sid > 2 order by sname", null, "kim|pat|sue");
      check(planner, tx, "select grade, countofeid from gradecount order by countofeid desc, grade", null, "A 3|B 1|B+ 1");
      check(planner, tx, "select distinct sname from mathview, enroll where sid = studentid order by sname", null, "amy|kim|sue");

      // updates keep both indexes in step with the table
      checkCount("insert", planner.executeUpdate("insert into student(sid, sname, majorid, gradyear) values (10, 'zoe', 20, 2023)", tx), 1);
      checkCount("update", planner.executeUpdate("update student set majorid = 30 where sid >= 8", tx), 3);
      checkCount("delete", planner.executeUpdate("delete from student where gradyear < 2021 and majorid <> 10", tx), 4);
      check(planner, tx, "select sname from student where majorid = 30 order by sname", "IndexSelect", "art|lee|zoe");
      check(planner, tx, "select sname from student where sid = 10", "IndexSelect", "zoe");
      check(planner, tx, "select sname from student where sid = 8", "IndexSelect", "");
      tx.rollback();

      tx = db.newTx();
      createExtensionTables(planner, tx);
      tx.commit();
      tx = db.newTx();

      section("Extension: double type");
      check(planner, tx, "select name, price from item where price > 10 order by price", null, "book 12.75|lamp 34.99");
      check(planner, tx, "select name from item where price = 2.5 order by name", null, "cup|pen");
      check(planner, tx, "select count(iid), sum(price), min(price), max(price) from item", null, "5 62.74 2.5 34.99");
      check(planner, tx, "select avg(iid) from item", null, "3.0");
      check(planner, tx, "select name from item where price = 10", "IndexSelect(price = 10, hash", "mug");
      check(planner, tx, "select name, did from item, dept where price = did", null, "mug 10");
      checkRejected(planner, tx, "insert into item(iid, name, price) values (2.5, 'x', 1)", "cannot store 2.5 (double) in iid (int)");

      section("Extension: qualified names");
      check(planner, tx, "select t.name, s.name from t, s where t.a1 = s.a1 order by t.name", null, "x p|y q|z p");
      check(planner, tx, "select x.a, y.b from t x, s y where x.a1 = y.a1 and y.b > 150", null, "2 200");
      check(planner, tx, "select s1.sname, s2.sname from student s1, student s2 where s1.majorid = s2.majorid "
            + "and s1.sid < s2.sid and s1.majorid = 30", null, "bob art");
      checkRejected(planner, tx, "select name from t, s", "field name is ambiguous: it could be t.name or s.name");
      checkRejected(planner, tx, "select q.a from t", "unknown table or alias q in q.a");

      section("Extension: multi-way sort");
      List<String> expected = new ArrayList<>();
      for (int b=1499; b>=0; b--)
         if (b % 97 == 5)
            expected.add(String.valueOf(b));
      check(planner, tx, "select b from bigr where a = 5 order by b desc", "Sort(bigr.b desc)", String.join("|", expected));
      check(planner, tx, "select count(b), min(b), max(b) from bigr where b >= 750", null, "750 750 1499");

      section("Extension: set operations");
      check(planner, tx, "select sid from student intersect select studentid from enroll order by sid", null, "1|2|4|6");
      check(planner, tx, "select studentid from enroll except all select sid from student where sid < 5 order by studentid", null, "1|4|6");
      check(planner, tx, "select did from dept union select sid from student intersect select studentid from enroll order by did",
            null, "1|2|4|6|10|20|30");
      check(planner, tx, "select majorid from student union all select did from dept order by majorid", null,
            "10|10|10|10|20|20|20|20|20|30|30|30");
      checkRejected(planner, tx, "select sname from student union select sid from student", "cannot union sname (varchar) with sid (int)");

      section("Extension: query validation");
      checkRejected(planner, tx, "select nosuch from student", "unknown field nosuch");
      checkRejected(planner, tx, "select sname from nosuch", "unknown table nosuch");
      checkRejected(planner, tx, "select sname from student where sname = 10", "cannot compare student.sname (varchar) with 10 (int) in student.sname=10");
      checkRejected(planner, tx, "select sum(sname) from student", "sum(sname) needs a numeric field, but sname is a varchar");
      checkRejected(planner, tx, "select sname from student order by sname extra", "unexpected text at extra");
      checkRejected(planner, tx, "insert into student(sid, sname) values (99, 'abcdefghijk')", "'abcdefghijk' is longer than sname (varchar(10))");
      checkRejected(planner, tx, "insert into student(sid, nosuch) values (1, 2)", "table student has no field nosuch");
      checkRejected(planner, tx, "delete from student where sname > 3", "cannot compare sname (varchar) with 3 (int) in sname>3");
      checkRejected(planner, tx, "create table student(a int)", "a table named student already exists");

      section("Extension: cost-based optimizer");
      Planner heurplanner = new Planner(new HeuristicQueryPlanner(db.mdMgr()), new IndexUpdatePlanner(db.mdMgr()), db.mdMgr());
      Planner costplanner = new Planner(new CostBasedQueryPlanner(db.mdMgr()), new IndexUpdatePlanner(db.mdMgr()), db.mdMgr());
      CostModel model = new CostModel(tx);
      String[] joins = {
         "select sname, title from student, enroll, section, course where sid = studentid and sectionid = sectid and courseid = cid order by sname, title",
         "select dname, count(eid) from student, enroll, dept where sid = studentid and majorid = did group by dname order by dname",
         "select b, h from bigr, bigu where a = g and b < 20 and h < 200 order by b, h",
      };
      for (String q : joins) {
         Plan hp = heurplanner.createQueryPlan(q, tx), cp = costplanner.createQueryPlan(q, tx);
         report(rows(hp).equals(rows(cp)), "same rows from both optimizers: " + q, "the cost-based plan's rows differ");
         long hc = model.cost(hp), cc = model.cost(cp);
         report(cc <= hc, "cost-based plan is no more costly (" + cc + " vs " + hc + ")", "estimated " + cc + " > " + hc);
      }
      tx.commit();

      System.out.println();
      System.out.println(passed + " passed, " + failed + " failed");
      if (failed > 0)
         System.exit(1);
   }

   // Two tables too large to hash join in one pass, so the planner uses a merge join.
   private static void createLargeTables(Planner planner, Transaction tx) {
      planner.executeUpdate("create table bigr(a int, b int)", tx);
      planner.executeUpdate("create table bigu(g int, h int)", tx);
      for (int i=0; i<1500; i++)
         planner.executeUpdate("insert into bigr(a, b) values (" + (i % 97) + ", " + i + ")", tx);
      for (int i=0; i<1300; i++)
         planner.executeUpdate("insert into bigu(g, h) values (" + (i % 89) + ", " + i + ")", tx);
      System.out.println("Tables BIGR and BIGU created.");
   }

   private static void section(String title) {
      System.out.println();
      System.out.println("== " + title);
   }

   /**
    * Runs the query and compares its rows (in order) with the expected
    * ones, given as "|"-separated rows of space-separated values.
    * If planCheck is not null, the plan display must contain it,
    * or must not contain it if it starts with "!".
    */
   private static void check(Planner planner, Transaction tx, String qry, String planCheck, String expected) {
      String result;
      try {
         Plan p = planner.createQueryPlan(qry, tx);
         String plan = Plans.explain(p);
         if (planCheck != null && HEURISTIC) {
            boolean negate = planCheck.startsWith("!");
            String text = negate ? planCheck.substring(1) : planCheck;
            if (plan.contains(text) == negate) {
               report(false, qry, "plan " + (negate ? "uses " : "does not use ") + text + ":\n" + plan);
               return;
            }
         }
         List<String> rows = new ArrayList<>();
         Scan s = p.open();
         while (s.next()) {
            List<String> vals = new ArrayList<>();
            for (String fldname : p.schema().fields())
               vals.add(s.getVal(fldname).toString());
            rows.add(String.join(" ", vals));
         }
         s.close();
         result = String.join("|", rows);
      }
      catch (RuntimeException e) {
         result = "exception " + e;
      }
      report(result.equals(expected), qry, "got      " + result + "\n        expected " + expected);
   }

   // a table with double fields, and two tables that share field names
   private static void createExtensionTables(Planner planner, Transaction tx) {
      planner.executeUpdate("create table item(iid int, name varchar(10), price double)", tx);
      String[] items = {"(1, 'pen', 2.5)", "(2, 'book', 12.75)", "(3, 'mug', 10)", "(4, 'lamp', 34.99)", "(5, 'cup', 2.5)"};
      for (String v : items)
         planner.executeUpdate("insert into item(iid, name, price) values " + v, tx);
      planner.executeUpdate("create index item_price on item(price) using hash", tx);
      planner.executeUpdate("create table t(a int, a1 int, name varchar(5))", tx);
      planner.executeUpdate("create table s(a1 int, b int, name varchar(5))", tx);
      for (String v : new String[]{"(1, 10, 'x')", "(2, 20, 'y')", "(3, 10, 'z')"})
         planner.executeUpdate("insert into t(a, a1, name) values " + v, tx);
      for (String v : new String[]{"(10, 100, 'p')", "(20, 200, 'q')", "(30, 300, 'r')"})
         planner.executeUpdate("insert into s(a1, b, name) values " + v, tx);
      System.out.println("Tables ITEM, T and S created.");
   }

   private static List<String> rows(Plan p) {
      List<String> rows = new ArrayList<>();
      Scan s = p.open();
      while (s.next()) {
         List<String> vals = new ArrayList<>();
         for (String fldname : p.schema().fields())
            vals.add(s.getVal(fldname).toString());
         rows.add(String.join(" ", vals));
      }
      s.close();
      return rows;
   }

   // the statement must be rejected with the specified message
   private static void checkRejected(Planner planner, Transaction tx, String stmt, String message) {
      String got;
      try {
         if (stmt.startsWith("select"))
            planner.createQueryPlan(stmt, tx);
         else
            planner.executeUpdate(stmt, tx);
         got = "accepted";
      }
      catch (simpledb.parse.BadSyntaxException e) {
         got = e.getMessage();
      }
      report(message.equals(got), "rejected: " + stmt, "got \"" + got + "\", expected \"" + message + "\"");
   }

   private static void checkRejected(Planner planner, Transaction tx, String qry) {
      boolean rejected;
      try {
         planner.createQueryPlan(qry, tx);
         rejected = false;
      }
      catch (simpledb.parse.BadSyntaxException e) {
         rejected = true;
      }
      report(rejected, "rejected: " + qry, "was accepted");
   }

   private static void checkCount(String what, int actual, int expected) {
      report(actual == expected, what + " changes " + expected + " records", "changed " + actual);
   }

   private static void report(boolean ok, String what, String detail) {
      if (ok) {
         passed++;
         System.out.println("  PASS  " + what);
      }
      else {
         failed++;
         System.out.println("  FAIL  " + what + "\n        " + detail);
      }
   }

   private static void deleteDirectory(File dir) {
      File[] files = dir.listFiles();
      if (files == null)
         return;
      for (File f : files)
         f.delete();
      dir.delete();
   }
}
