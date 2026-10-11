package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

public class OrderByTest {
    public static void main(String[] args) throws Exception {
        SimpleDB db = new SimpleDB("studentdb");
        Transaction tx = db.newTx();
        Planner planner = db.planner();
        String qry = "select SId, Sname, GradYear from student order by GradYear asc, Sname desc";
        try {
            Plan p = planner.createQueryPlan(qry, tx);
            Scan s = p.open();
            while (s.next())
                System.out.println(s.getInt("sid") + " " + s.getString("sname") + " " + s.getInt("gradyear"));
            s.close();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            tx.rollback();
        }
    }
}