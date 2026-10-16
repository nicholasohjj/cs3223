package simpledb.plan;

import simpledb.tx.Transaction;
import simpledb.parse.*;
import simpledb.metadata.MetadataMgr;

/**
 * The object that executes SQL statements.
 * @author Edward Sciore
 */
public class Planner {
   private QueryPlanner qplanner;
   private UpdatePlanner uplanner;
   private MetadataMgr mdm;
   
   public Planner(QueryPlanner qplanner, UpdatePlanner uplanner) {
      this(qplanner, uplanner, null);
   }

   /**
    * Creates a planner that also validates update statements
    * against the catalog before executing them.
    * @param mdm the metadata manager
    */
   public Planner(QueryPlanner qplanner, UpdatePlanner uplanner, MetadataMgr mdm) {
      this.qplanner = qplanner;
      this.uplanner = uplanner;
      this.mdm = mdm;
   }
   
   /**
    * Creates a plan for an SQL select statement, using the supplied planner.
    * @param qry the SQL query string
    * @param tx the transaction
    * @return the scan corresponding to the query plan
    */
   public Plan createQueryPlan(String qry, Transaction tx) {
      Parser parser = new Parser(qry);
      QueryData data = parser.query();
      parser.checkEnd();
      verifyQuery(data);
      return qplanner.createPlan(data, tx);
   }
   
   /**
    * Executes an SQL insert, delete, modify, or
    * create statement.
    * The method dispatches to the appropriate method of the
    * supplied update planner,
    * depending on what the parser returns.
    * @param cmd the SQL update string
    * @param tx the transaction
    * @return an integer denoting the number of affected records
    */
   public int executeUpdate(String cmd, Transaction tx) {
      Parser parser = new Parser(cmd);
      Object data = parser.updateCmd();
      parser.checkEnd();
      data = verifyUpdate(data, tx);
      if (data instanceof InsertData)
         return uplanner.executeInsert((InsertData)data, tx);
      else if (data instanceof DeleteData)
         return uplanner.executeDelete((DeleteData)data, tx);
      else if (data instanceof ModifyData)
         return uplanner.executeModify((ModifyData)data, tx);
      else if (data instanceof CreateTableData)
         return uplanner.executeCreateTable((CreateTableData)data, tx);
      else if (data instanceof CreateViewData)
         return uplanner.executeCreateView((CreateViewData)data, tx);
      else if (data instanceof CreateIndexData)
         return uplanner.executeCreateIndex((CreateIndexData)data, tx);
      else
         return 0;
   }
 
   // A query's names and types are checked when it is planned,
   // by QueryResolver (and SetQueryPlanner for set operations).
   private void verifyQuery(QueryData data) {
   }

   // Checks an update statement against the catalog, and returns
   // the statement to execute; see UpdateValidator.
   private Object verifyUpdate(Object data, Transaction tx) {
      return (mdm == null) ? data : new UpdateValidator(mdm, tx).validate(data);
   }
}
