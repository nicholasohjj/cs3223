package simpledb.parse;

import java.util.*;

import simpledb.materialize.*;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   public String field() {
      return lex.eatId();
   }
   
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else if (lex.matchDoubleConstant())
         return new Constant(lex.eatDoubleConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   public Term term() {
      Expression lhs = expression();
      String opr = lex.eatOpr();
      Expression rhs = expression();
      return new Term(lhs, rhs, opr);
   }
   
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }

   public List<OrderByElement> orderByList() {
      List<OrderByElement> L = new ArrayList<>();
      L.add(orderByElement());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(orderByList());
      }
      return L;
   }

   public OrderByElement orderByElement() {
      String fld = field();
      boolean asc = true;
      if (lex.matchKeyword("asc")) {
         lex.eatKeyword("asc");
      } else if (lex.matchKeyword("desc")){
         lex.eatKeyword("desc");
         asc = false;
      }
      return new OrderByElement(fld, asc);
   }
   
   /**
    * Throws an exception if anything follows the statement that
    * has been parsed, e.g. a misspelled clause after a query.
    */
   public void checkEnd() {
      if (!lex.matchEnd())
         throw new BadSyntaxException("unexpected text at " + lex.currentToken());
   }

// Methods for parsing queries
   
   /**
    * Parses a query: a select statement, or a set operation between
    * queries, optionally followed by an order by clause that sorts
    * the whole result.  Intersect binds more tightly than union and
    * except, which are evaluated from left to right, as in SQL.
    */
   public QueryData query() {
      QueryData q = intersectQuery();
      while (lex.matchKeyword("union") || lex.matchKeyword("except")) {
         String op = lex.matchKeyword("union") ? "union" : "except";
         lex.eatKeyword(op);
         boolean all = eatAll();
         q = new SetQueryData(q, op, all, intersectQuery(), new ArrayList<>());
      }
      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         q = q.withOrderBy(orderByList());
      }
      return q;
   }

   private QueryData intersectQuery() {
      QueryData q = selectQuery();
      while (lex.matchKeyword("intersect")) {
         lex.eatKeyword("intersect");
         boolean all = eatAll();
         q = new SetQueryData(q, "intersect", all, selectQuery(), new ArrayList<>());
      }
      return q;
   }

   private boolean eatAll() {
      if (!lex.matchKeyword("all"))
         return false;
      lex.eatKeyword("all");
      return true;
   }

   /**
    * Parses a single select statement, without an order by clause.
    */
   private QueryData selectQuery() {
      lex.eatKeyword("select");
      boolean distinct = false;
      if (lex.matchKeyword("distinct")) {
         lex.eatKeyword("distinct");
         distinct = true;
      }
      List<String> fields = new ArrayList<>();
      List<AggregationFn> aggFns = new ArrayList<>();
      selectList(fields, aggFns);
      lex.eatKeyword("from");
      List<String> tables = new ArrayList<>();
      List<String> aliases = new ArrayList<>();
      tableList(tables, aliases);
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupByFields = new ArrayList<>();
      if (lex.matchKeyword("group")) {
         lex.eatKeyword("group");
         lex.eatKeyword("by");
         groupByFields = fieldList();
      }
      // checks that depend on what the names refer to (such as grouped
      // fields) are made once the names are resolved; see QueryResolver
      return new QueryData(fields, tables, aliases, pred, new ArrayList<>(), groupByFields, aggFns, distinct);
   }

   /**
    * Parses the select clause into a list of output field names
    * and a (possibly empty) list of aggregation functions.
    * Each element is either a plain field name (e.g. "sname"),
    * which is added to fields, or an aggregate function call
    * such as "sum(sal)", which is added to aggFns and whose
    * generated output field name (e.g. "sumofsal") is added
    * to fields.
    * @param fields the list to be filled with output field names
    * @param aggFns the list to be filled with aggregation functions
    */
   private void selectList(List<String> fields, List<AggregationFn> aggFns) {
      String id = lex.eatId();
      if (lex.matchDelim('(')) {
         AggregationFn fn = aggregationFn(id);
         aggFns.add(fn);
         fields.add(fn.fieldName());
      }
      else
         fields.add(id);
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         selectList(fields, aggFns);
      }
   }

   /**
    * Parses an aggregate function call, given its already-parsed
    * function name, e.g. "count(sid)" or "sum(sal)".
    * @param fnname the (lower-cased) name of the aggregation function:
    *               one of count, sum, avg, min, max
    * @return the aggregation function for the parenthesized field
    */
   private AggregationFn aggregationFn(String fnname) {
      lex.eatDelim('(');
      String fldname = field();
      lex.eatDelim(')');
      switch (fnname) {
         case "count":
            return new CountFn(fldname);
         case "sum":
            return new SumFn(fldname);
         case "avg":
            return new AvgFn(fldname);
         case "min":
            return new MinFn(fldname);
         case "max":
            return new MaxFn(fldname);
         default:
            throw new BadSyntaxException();
      }
   }
   
   /**
    * Parses the from clause: a list of table names, each
    * optionally followed by an alias, e.g. "student s, enroll".
    * @param tables the list to be filled with table names
    * @param aliases the list to be filled with each table's alias (or null)
    */
   private void tableList(List<String> tables, List<String> aliases) {
      tables.add(lex.eatId());
      aliases.add(lex.matchId() ? lex.eatId() : null);
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         tableList(tables, aliases);
      }
   }
   
// Methods for parsing the various update commands
   
   public Object updateCmd() {
      if (lex.matchKeyword("insert"))
         return insert();
      else if (lex.matchKeyword("delete"))
         return delete();
      else if (lex.matchKeyword("update"))
         return modify();
      else
         return create();
   }
   
   private Object create() {
      lex.eatKeyword("create");
      if (lex.matchKeyword("table"))
         return createTable();
      else if (lex.matchKeyword("view"))
         return createView();
      else
         return createIndex();
   }
   
// Method for parsing delete commands
   
   public DeleteData delete() {
      lex.eatKeyword("delete");
      lex.eatKeyword("from");
      String tblname = lex.eatId();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new DeleteData(tblname, pred);
   }
   
// Methods for parsing insert commands
   
   public InsertData insert() {
      lex.eatKeyword("insert");
      lex.eatKeyword("into");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      List<String> flds = fieldList();
      lex.eatDelim(')');
      lex.eatKeyword("values");
      lex.eatDelim('(');
      List<Constant> vals = constList();
      lex.eatDelim(')');
      return new InsertData(tblname, flds, vals);
   }
   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
   private List<Constant> constList() {
      List<Constant> L = new ArrayList<Constant>();
      L.add(constant());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(constList());
      }
      return L;
   }
   
// Method for parsing modify commands
   
   public ModifyData modify() {
      lex.eatKeyword("update");
      String tblname = lex.eatId();
      lex.eatKeyword("set");
      String fldname = field();
      lex.eatEquals();
      Expression newval = expression();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new ModifyData(tblname, fldname, newval, pred);
   }
   
// Method for parsing create table commands
   
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
   private Schema fieldType(String fldname) {
      Schema schema = new Schema();
      if (lex.matchKeyword("int")) {
         lex.eatKeyword("int");
         schema.addIntField(fldname);
      }
      else if (lex.matchKeyword("double") || lex.matchKeyword("float")) {
         lex.eatKeyword(lex.matchKeyword("double") ? "double" : "float");
         schema.addDoubleField(fldname);
      }
      else {
         lex.eatKeyword("varchar");
         lex.eatDelim('(');
         int strLen = lex.eatIntConstant();
         lex.eatDelim(')');
         schema.addStringField(fldname, strLen);
      }
      return schema;
   }
   
// Method for parsing create view commands
   
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
   public CreateIndexData createIndex() {
      lex.eatKeyword("index");
      String idxname = lex.eatId();
      lex.eatKeyword("on");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      String fldname = field();
      lex.eatDelim(')');
      String structure = "hash";
      if (lex.matchKeyword("using")) {
         lex.eatKeyword("using");
         if (lex.matchKeyword("btree"))
            structure = "btree";
         lex.eatKeyword(structure);
      }
      return new CreateIndexData(idxname, tblname, fldname, structure);
   }
}

