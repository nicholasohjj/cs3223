package simpledb.plan;

import static java.sql.Types.*;
import java.util.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * Checks update statements (insert, delete, update, and the create
 * statements) against the catalog before they are executed, so that
 * a bad statement fails with a clear BadQueryException instead of
 * failing part-way or corrupting data (e.g. a string longer than its
 * varchar field would overwrite the next field).
 * <p>
 * A delete or update may name the table's fields qualified by the
 * table name ("student.sid"); the validated statement refers to them
 * by their plain names, which is what the update planners use.
 */
public class UpdateValidator {
   private MetadataMgr mdm;
   private Transaction tx;

   public UpdateValidator(MetadataMgr mdm, Transaction tx) {
      this.mdm = mdm;
      this.tx = tx;
   }

   /**
    * Validates the specified update statement.
    * @param data the parsed statement
    * @return the statement to execute (with its field names unqualified)
    * @throws BadQueryException if the statement is invalid
    */
   public Object validate(Object data) {
      if (data instanceof InsertData)
         return validateInsert((InsertData) data);
      if (data instanceof DeleteData)
         return validateDelete((DeleteData) data);
      if (data instanceof ModifyData)
         return validateModify((ModifyData) data);
      if (data instanceof CreateTableData)
         return validateCreateTable((CreateTableData) data);
      if (data instanceof CreateViewData)
         return validateCreateView((CreateViewData) data);
      if (data instanceof CreateIndexData)
         return validateCreateIndex((CreateIndexData) data);
      return data;
   }

   private InsertData validateInsert(InsertData data) {
      Schema sch = storedTable(data.tableName(), "insert into");
      List<String> fields = data.fields();
      List<Constant> vals = data.vals();
      if (fields.size() != vals.size())
         throw new BadQueryException("insert lists " + fields.size() + " fields but "
               + vals.size() + " values");
      List<String> plain = new ArrayList<>();
      for (int i=0; i<fields.size(); i++) {
         String fldname = field(fields.get(i), data.tableName(), sch);
         if (plain.contains(fldname))
            throw new BadQueryException("field " + fldname + " is listed twice");
         checkValue(sch, fldname, vals.get(i));
         plain.add(fldname);
      }
      return new InsertData(data.tableName(), plain, vals);
   }

   private DeleteData validateDelete(DeleteData data) {
      Schema sch = storedTable(data.tableName(), "delete from");
      return new DeleteData(data.tableName(), predicate(data.pred(), data.tableName(), sch));
   }

   private ModifyData validateModify(ModifyData data) {
      String tblname = data.tableName();
      Schema sch = storedTable(tblname, "update");
      String target = field(data.targetField(), tblname, sch);
      Expression newval = data.newValue().rename(f -> field(f, tblname, sch));
      if (newval.isFieldName()) {
         String src = newval.asFieldName();
         if ((sch.type(src) == VARCHAR) != (sch.type(target) == VARCHAR)
               || (sch.type(src) == DOUBLE && sch.type(target) == INTEGER))
            throw new BadQueryException("cannot set " + target + " (" + typeName(sch.type(target))
                  + ") to " + src + " (" + typeName(sch.type(src)) + ")");
         if (sch.type(target) == VARCHAR && sch.length(src) > sch.length(target))
            throw new BadQueryException("cannot set " + target + " (varchar(" + sch.length(target)
                  + ")) to " + src + " (varchar(" + sch.length(src) + "))");
      }
      else
         checkValue(sch, target, newval.asConstant());
      return new ModifyData(tblname, target, newval, predicate(data.pred(), tblname, sch));
   }

   private CreateTableData validateCreateTable(CreateTableData data) {
      checkNewName(data.tableName(), "table");
      Schema sch = data.newSchema();
      Set<String> seen = new HashSet<>();
      for (String fldname : sch.fields()) {
         if (fldname.contains("."))
            throw new BadQueryException("field name " + fldname + " cannot contain a '.'");
         if (!seen.add(fldname))
            throw new BadQueryException("field " + fldname + " is defined twice");
         if (sch.type(fldname) == VARCHAR && sch.length(fldname) < 1)
            throw new BadQueryException("varchar field " + fldname + " must have a length of at least 1");
      }
      return data;
   }

   private CreateViewData validateCreateView(CreateViewData data) {
      checkNewName(data.viewName(), "view");
      QueryResolver.outputSchemaOf(data.queryData(), mdm, tx);   // the query must be valid
      return data;
   }

   private CreateIndexData validateCreateIndex(CreateIndexData data) {
      Schema sch = storedTable(data.tableName(), "create an index on");
      String fldname = field(data.fieldName(), data.tableName(), sch);
      if (mdm.indexExists(data.indexName(), tx))
         throw new BadQueryException("an index named " + data.indexName() + " already exists");
      if (mdm.getIndexInfo(data.tableName(), tx).containsKey(fldname))
         throw new BadQueryException("field " + fldname + " of " + data.tableName() + " is already indexed");
      return new CreateIndexData(data.indexName(), data.tableName(), fldname, data.structure());
   }

   // the schema of a stored table (not a view) that the statement changes
   private Schema storedTable(String tblname, String action) {
      if (mdm.getViewDef(tblname, tx) != null)
         throw new BadQueryException("cannot " + action + " " + tblname + ", which is a view");
      Schema sch = mdm.getLayout(tblname, tx).schema();
      if (sch.fields().isEmpty())
         throw new BadQueryException("unknown table " + tblname);
      return sch;
   }

   private void checkNewName(String name, String kind) {
      if (name.contains("."))
         throw new BadQueryException(kind + " name " + name + " cannot contain a '.'");
      if (mdm.getViewDef(name, tx) != null)
         throw new BadQueryException("a view named " + name + " already exists");
      if (!mdm.getLayout(name, tx).schema().fields().isEmpty())
         throw new BadQueryException("a table named " + name + " already exists");
   }

   // a field of the table, written plain or qualified by the table name
   private String field(String fldname, String tblname, Schema sch) {
      String plain = fldname;
      int dot = fldname.indexOf('.');
      if (dot >= 0) {
         if (!fldname.substring(0, dot).equals(tblname))
            throw new BadQueryException(fldname + " does not refer to table " + tblname);
         plain = fldname.substring(dot + 1);
      }
      if (!sch.hasField(plain))
         throw new BadQueryException("table " + tblname + " has no field " + plain);
      return plain;
   }

   private Predicate predicate(Predicate pred, String tblname, Schema sch) {
      Predicate result = pred.rename(f -> field(f, tblname, sch));
      for (Term t : result.terms()) {
         int ltype = typeOf(t.lhs(), sch), rtype = typeOf(t.rhs(), sch);
         if ((ltype == VARCHAR) != (rtype == VARCHAR))
            throw new BadQueryException("cannot compare " + t.lhs() + " (" + typeName(ltype) + ") with "
                  + t.rhs() + " (" + typeName(rtype) + ") in " + t);
      }
      return result;
   }

   private static int typeOf(Expression e, Schema sch) {
      return e.isFieldName() ? sch.type(e.asFieldName()) : e.asConstant().type();
   }

   // a constant stored in a field must have a compatible type, and fit
   private static void checkValue(Schema sch, String fldname, Constant val) {
      int type = sch.type(fldname);
      boolean ok = (type == VARCHAR) ? val.type() == VARCHAR
                 : (type == DOUBLE) ? val.isNumeric()
                 : val.type() == INTEGER;
      if (!ok)
         throw new BadQueryException("cannot store " + (val.type() == VARCHAR ? "'" + val + "'" : val)
               + " (" + typeName(val.type()) + ") in " + fldname + " (" + typeName(type) + ")");
      if (type == VARCHAR && val.asString().length() > sch.length(fldname))
         throw new BadQueryException("'" + val + "' is longer than " + fldname + " (varchar("
               + sch.length(fldname) + "))");
   }

   private static String typeName(int type) {
      return SetQueryPlanner.typeName(type);
   }
}
