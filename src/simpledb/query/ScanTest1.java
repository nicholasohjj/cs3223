package simpledb.query;

import java.util.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.record.*;

public class ScanTest1 {
   public static void main(String[] args) throws Exception {
      SimpleDB db = new SimpleDB("scantest1");
      Transaction tx = db.newTx();

      Schema sch1 = new Schema();
      sch1.addIntField("A");
      sch1.addStringField("B", 9);
      Layout layout = new Layout(sch1);
      UpdateScan s1 = new TableScan(tx, "T", layout);

      s1.beforeFirst();
      int n = 200;
      System.out.println("Inserting " + n + " random records.");
      for (int i=0; i<n; i++) {
         s1.insert();
         int k = (int) Math.round(Math.random() * 50);
         s1.setInt("A", k);
         s1.setString("B", "rec"+k);
      }
      s1.close();

      Scan s2 = new TableScan(tx, "T", layout);
      // selecting all records where A=10
      Constant c = new Constant(10);
      Term t = new Term(new Expression("A"), new Expression(c)); 
      Predicate pred = new Predicate(t);
      System.out.println("The predicate is " + pred);
      Scan s3 = new SelectScan(s2, pred);
      List<String> fields = Arrays.asList("B");
      Scan s4 = new ProjectScan(s3, fields);
      while (s4.next())
         System.out.println(s4.getString("B"));
      s4.close();

      // selecting with each non-equality operator, on an int and a string field
      String[] oprs = {"<", "<=", ">", ">=", "!=", "<>"};
      for (String opr : oprs) {
         checkCount(tx, layout, "A", new Constant(10), opr);
         checkCount(tx, layout, "B", new Constant("rec25"), opr);
      }
      tx.commit();
   }

   // compares the number of records selected by "fld opr val" against a brute-force count
   private static void checkCount(Transaction tx, Layout layout, String fld, Constant val, String opr) {
      Term t = new Term(new Expression(fld), new Expression(val), opr);
      Scan s = new SelectScan(new TableScan(tx, "T", layout), new Predicate(t));
      int actual = 0;
      while (s.next())
         actual++;
      s.close();

      Scan all = new TableScan(tx, "T", layout);
      int expected = 0;
      while (all.next()) {
         if (satisfies(all.getVal(fld).compareTo(val), opr))
            expected++;
      }
      all.close();
      System.out.println("The predicate " + t + " selects " + actual + " records"
            + (actual == expected ? " (ok)" : " (EXPECTED " + expected + ")"));
   }

   private static boolean satisfies(int cmp, String opr) {
      return switch (opr) {
         case "<" -> cmp < 0;
         case "<=" -> cmp <= 0;
         case ">" -> cmp > 0;
         case ">=" -> cmp >= 0;
         default -> cmp != 0;
      };
   }
}
