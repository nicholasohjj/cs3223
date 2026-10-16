package simpledb.plan;

import static java.sql.Types.*;
import java.util.*;
import simpledb.materialize.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * Resolves the names in a query to the fields they refer to.
 * Each table in the from clause is known by its alias (by default,
 * its own name), and each of its fields by the qualified name
 * "alias.field".  A field may be written qualified ("s.sid") or,
 * if exactly one table has a field of that name, unqualified ("sid").
 * <p>
 * The result is an equivalent query in which every field name is
 * qualified (so tables may share field names, and a table may be
 * joined with itself), together with the name of each output field.
 * As in SQL, a selected field is named by its attribute ("s.sid"
 * gives "sid"), unless two selected fields would then share a name,
 * in which case they keep the names the query wrote for them.
 * An aggregate is named as before, e.g. "countofsid".
 * Names that do not resolve, comparisons between a string and a
 * number, sum or avg of a string field, and fields used incorrectly
 * in a grouped or distinct query, cause a BadQueryException.
 */
public class QueryResolver {
   private Map<String,Schema> columns = new LinkedHashMap<>();  // alias -> its fields
   private Map<String,String> aggnames = new HashMap<>();   // aggregate output name -> qualified form
   private Map<String,String> selectnames = new HashMap<>(); // output name of a selected field -> qualified form
   private QueryData query;
   private Map<String,String> outputnames = new LinkedHashMap<>();
   private Schema outputschema = new Schema();

   /**
    * Resolves the names of the specified query.
    * @param data the query, as parsed
    * @param mdm the metadata manager, for the tables' and views' fields
    * @param tx the calling transaction
    * @throws BadQueryException if a name is unknown or ambiguous,
    *         or a field is used incorrectly
    */
   public QueryResolver(QueryData data, MetadataMgr mdm, Transaction tx) {
      Iterator<String> aliases = data.aliases().iterator();
      for (String tblname : data.tables()) {
         String alias = aliases.next();
         if (columns.containsKey(alias))
            throw new BadQueryException("table or alias " + alias + " appears more than once in the from clause");
         columns.put(alias, fieldsOf(tblname, mdm, tx));
      }

      List<AggregationFn> aggfns = new ArrayList<>();
      for (AggregationFn fn : data.aggFns()) {
         AggregationFn resolved = fn.withField(resolve(fn.argField()));
         if ((fn instanceof SumFn || fn instanceof AvgFn) && type(resolved.argField()) == VARCHAR)
            throw new BadQueryException(fn + " needs a numeric field, but " + fn.argField() + " is a varchar");
         aggnames.put(fn.fieldName(), resolved.fieldName());
         aggfns.add(resolved);
      }

      List<String> fields = new ArrayList<>();
      List<String> names = outputNames(data);
      for (int i=0; i<data.fields().size(); i++) {
         String resolved = resolveOutput(data.fields().get(i));
         fields.add(resolved);
         outputnames.putIfAbsent(resolved, names.get(i));
         selectnames.putIfAbsent(names.get(i), resolved);
      }

      List<String> groupfields = new ArrayList<>();
      for (String fldname : data.groupByFields())
         groupfields.add(resolve(fldname));

      List<OrderByElement> orderby = new ArrayList<>();
      for (OrderByElement e : data.orderByList())
         orderby.add(new OrderByElement(resolveOutput(e.field()), e.isAscending()));

      Predicate pred = data.pred().rename(this::resolve);
      for (Term t : pred.terms())
         checkComparable(t);

      if (!groupfields.isEmpty() || !aggfns.isEmpty()) {
         // no other field has a single value per group
         Set<String> allowed = new HashSet<>(groupfields);
         for (AggregationFn fn : aggfns)
            allowed.add(fn.fieldName());
         for (String fldname : fields)
            if (!allowed.contains(fldname))
               throw new BadQueryException("field " + outputnames.get(fldname)
                     + " must be in the group by clause or used in an aggregate");
         for (OrderByElement e : orderby)
            if (!allowed.contains(e.field()))
               throw new BadQueryException("order by field " + e.field()
                     + " must be in the group by clause or be an aggregate");
      }
      if (data.isDistinct())
         // the distinct sort also does the ordering, so it can
         // only order on fields that appear in the output
         for (OrderByElement e : orderby)
            if (!fields.contains(e.field()))
               throw new BadQueryException("with distinct, order by field " + e.field()
                     + " must be in the select list");

      query = new QueryData(fields, data.tables(), data.aliases(), pred, orderby,
                            groupfields, aggfns, data.isDistinct());

      // the output fields and their types, e.g. for a view over this query
      Map<String,AggregationFn> aggbyname = new HashMap<>();
      for (AggregationFn fn : aggfns)
         aggbyname.put(fn.fieldName(), fn);
      for (int i=0; i<fields.size(); i++) {
         String fldname = fields.get(i), outname = names.get(i);
         if (outputschema.hasField(outname))
            continue;
         if (aggbyname.containsKey(fldname)) {
            Schema aggsch = new Schema();
            aggbyname.get(fldname).addToSchema(aggsch, qualifiedSchema());
            outputschema.addField(outname, aggsch.type(fldname), aggsch.length(fldname));
         }
         else
            outputschema.addField(outname, type(fldname), length(fldname));
      }
   }

   /**
    * Returns the output fields of the query, by their output names,
    * with their types.
    */
   public Schema outputSchema() {
      return outputschema;
   }

   /**
    * Returns the query with every field name qualified.
    */
   public QueryData query() {
      return query;
   }

   /**
    * Returns the name of each output field: maps the qualified
    * name of a selected field to the name the query used for it.
    */
   public Map<String,String> outputNames() {
      return outputnames;
   }

   /**
    * Returns the qualified name of the specified field.
    * @param fldname a field name, qualified or not
    * @return the qualified name
    */
   public String resolve(String fldname) {
      int dot = fldname.indexOf('.');
      if (dot >= 0) {
         String alias = fldname.substring(0, dot);
         String field = fldname.substring(dot + 1);
         if (!columns.containsKey(alias))
            throw new BadQueryException("unknown table or alias " + alias + " in " + fldname);
         if (!columns.get(alias).hasField(field))
            throw new BadQueryException("table " + alias + " has no field " + field);
         return fldname;
      }
      List<String> matches = new ArrayList<>();
      for (Map.Entry<String,Schema> e : columns.entrySet())
         if (e.getValue().hasField(fldname))
            matches.add(e.getKey());
      if (matches.isEmpty())
         throw new BadQueryException("unknown field " + fldname);
      if (matches.size() > 1)
         throw new BadQueryException("field " + fldname + " is ambiguous: it could be "
               + String.join(" or ", matches.stream().map(a -> a + "." + fldname).toList()));
      return matches.get(0) + "." + fldname;
   }

   /**
    * Returns the output field names of the specified query, in order.
    * This depends only on how the select clause is written, so it also
    * gives the field names of a view without planning it.
    * @param data the query, as parsed
    * @return the name of each field of the select clause
    */
   public static List<String> outputNames(QueryData data) {
      if (data instanceof SetQueryData)   // a set operation has the left query's fields
         return outputNames(((SetQueryData) data).left());
      Set<String> aggs = new HashSet<>();
      for (AggregationFn fn : data.aggFns())
         aggs.add(fn.fieldName());
      Map<String,Integer> uses = new HashMap<>();
      for (String fldname : data.fields())
         uses.merge(attribute(fldname, aggs), 1, Integer::sum);
      List<String> names = new ArrayList<>();
      for (String fldname : data.fields()) {
         String attr = attribute(fldname, aggs);
         names.add(uses.get(attr) > 1 ? fldname : attr);
      }
      return names;
   }

   private static String attribute(String fldname, Set<String> aggs) {
      return aggs.contains(fldname) ? fldname : fldname.substring(fldname.indexOf('.') + 1);
   }

   // a selected or ordered field may also be an aggregate's output name;
   // an ordered field may also be the output name of a selected field
   private String resolveOutput(String fldname) {
      if (aggnames.containsKey(fldname))
         return aggnames.get(fldname);
      if (selectnames.containsKey(fldname))
         return selectnames.get(fldname);
      return resolve(fldname);
   }

   // the type of a resolved (qualified) field
   private int type(String qname) {
      int dot = qname.indexOf('.');
      return columns.get(qname.substring(0, dot)).type(qname.substring(dot + 1));
   }

   private int length(String qname) {
      int dot = qname.indexOf('.');
      return columns.get(qname.substring(0, dot)).length(qname.substring(dot + 1));
   }

   // all fields of the from clause, by their qualified names
   private Schema qualifiedSchema() {
      Schema sch = new Schema();
      for (Map.Entry<String,Schema> e : columns.entrySet())
         for (String fldname : e.getValue().fields())
            sch.addField(e.getKey() + "." + fldname, e.getValue().type(fldname), e.getValue().length(fldname));
      return sch;
   }

   // a string can only be compared with a string, and a number with a number
   private void checkComparable(Term t) {
      int ltype = typeOf(t.lhs()), rtype = typeOf(t.rhs());
      if ((ltype == VARCHAR) != (rtype == VARCHAR))
         throw new BadQueryException("cannot compare " + t.lhs() + " (" + SetQueryPlanner.typeName(ltype)
               + ") with " + t.rhs() + " (" + SetQueryPlanner.typeName(rtype) + ") in " + t);
   }

   private int typeOf(Expression e) {
      return e.isFieldName() ? type(e.asFieldName()) : e.asConstant().type();
   }

   /**
    * Returns the fields of the specified table or view, with their types.
    * A view's fields are the output fields of its (resolved) query.
    */
   static Schema fieldsOf(String tblname, MetadataMgr mdm, Transaction tx) {
      String viewdef = mdm.getViewDef(tblname, tx);
      if (viewdef != null)
         return outputSchemaOf(new Parser(viewdef).query(), mdm, tx);
      Schema sch = mdm.getLayout(tblname, tx).schema();
      if (sch.fields().isEmpty())
         throw new BadQueryException("unknown table " + tblname);
      return sch;
   }

   /**
    * Returns the output fields of the specified query, with their
    * types.  A set operation has the fields of its left query, with
    * an int widened to a double if the right query's field is a double.
    */
   static Schema outputSchemaOf(QueryData data, MetadataMgr mdm, Transaction tx) {
      if (!(data instanceof SetQueryData))
         return new QueryResolver(data, mdm, tx).outputSchema();
      SetQueryData sq = (SetQueryData) data;
      Schema left = outputSchemaOf(sq.left(), mdm, tx), right = outputSchemaOf(sq.right(), mdm, tx);
      Schema result = new Schema();
      List<String> rfields = right.fields();
      if (left.fields().size() != rfields.size())
         throw new BadQueryException("each side of " + sq.operation() + " must select the same number of fields, not "
               + left.fields().size() + " and " + rfields.size());
      for (int i=0; i<rfields.size(); i++)
         if ((left.type(left.fields().get(i)) == VARCHAR) != (right.type(rfields.get(i)) == VARCHAR))
            throw new BadQueryException("cannot " + sq.operation() + " " + left.fields().get(i)
                  + " with " + rfields.get(i) + ": one is a string and the other a number");
      for (int i=0; i<left.fields().size(); i++) {
         String f = left.fields().get(i);
         int type = left.type(f);
         if (type == INTEGER && i < rfields.size() && right.type(rfields.get(i)) == DOUBLE)
            type = DOUBLE;
         result.addField(f, type, Math.max(left.length(f), i < rfields.size() ? right.length(rfields.get(i)) : 0));
      }
      return result;
   }
}
