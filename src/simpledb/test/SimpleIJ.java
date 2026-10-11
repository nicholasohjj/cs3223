package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.util.Scanner;

import static java.sql.Types.INTEGER;

public class SimpleIJ {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        try {
            SimpleDB db = new SimpleDB("studentdb");
            Planner planner = db.planner();

            System.out.print("SQL> ");

            while (sc.hasNextLine()) {
                String cmd = sc.nextLine().trim();

                if (cmd.startsWith("exit")) {
                    break;
                } else if (cmd.startsWith("select")) {
                    doQuery(db, planner, cmd);
                } else {
                    doUpdate(db, planner, cmd);
                }

                System.out.print("\nSQL> ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sc.close();
    }


    private static void doQuery(SimpleDB db, Planner planner, String cmd) {
        Transaction tx = db.newTx();
        Scan s = null;

        try {
            // Create the query plan
            Plan p = planner.createQueryPlan(cmd, tx);

            Schema sch = p.schema();

            s = p.open();

            int totalwidth = 0;

            // Print header
            for (String fldname : sch.fields()) {
                int width = getDisplaySize(sch, fldname);
                totalwidth += width;

                String fmt = "%" + width + "s";
                System.out.format(fmt, fldname);
            }

            System.out.println();

            for (int i = 0; i < totalwidth; i++) {
                System.out.print("-");
            }

            System.out.println();

            // Print records
            while (s.next()) {
                for (String fldname : sch.fields()) {
                    int width = getDisplaySize(sch, fldname);
                    String fmt = "%" + width;

                    if (sch.type(fldname) == INTEGER) {
                        int val = s.getInt(fldname);
                        System.out.format(fmt + "d", val);
                    } else {
                        String val = s.getString(fldname);
                        System.out.format(fmt + "s", val);
                    }
                }

                System.out.println();
            }

            s.close();
            tx.commit();
        } catch (RuntimeException e) {
            if (s != null) {
                s.close();
            }

            tx.rollback();
            System.out.println("SimpleDB Exception: " + e.getMessage());
        }
    }


    private static void doUpdate(SimpleDB db, Planner planner, String cmd) {
        Transaction tx = db.newTx();

        try {
            int howmany = planner.executeUpdate(cmd, tx);

            tx.commit();

            System.out.println(howmany + " records processed");
        } catch (RuntimeException e) {
            tx.rollback();

            System.out.println("SimpleDB Exception: " + e.getMessage());
        }
    }


    private static int getDisplaySize(Schema sch, String fldname) {
        int fldtype = sch.type(fldname);

        int fldlength =
                (fldtype == INTEGER) ? 6 : sch.length(fldname);

        return Math.max(fldname.length(), fldlength) + 1;
    }
}