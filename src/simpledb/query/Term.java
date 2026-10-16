package simpledb.query;

import java.util.function.Function;
import simpledb.plan.Plan;
import simpledb.record.*;

/**
 * A term is a comparison between two expressions.
 * @author Edward Sciore
 *
 */

public class Term {
   private final Expression lhs;
    private Expression rhs;
   private final String opr;
   
   /**
    * Create a new term that compares two expressions
    * for equality.
    * @param lhs  the LHS expression
    * @param rhs  the RHS expression
    */
   public Term(Expression lhs, Expression rhs) {
      this(lhs, rhs, "=");
   }

   public Term(Expression lhs, Expression rhs, String opr) {
      this.lhs = lhs;
      this.rhs = rhs;
      this.opr = opr;
   }
   
   /**
    * Return true if both of the term's expressions
    * evaluate to the same constant,
    * with respect to the specified scan.
    * @param s the scan
    * @return true if both expressions have the same value in the scan
    */
   public boolean isSatisfied(Scan s) {
      Constant lhsval = lhs.evaluate(s);
      Constant rhsval = rhs.evaluate(s);

      return switch (opr) {
         case "=" ->  lhsval.equals(rhsval);
         case "!=", "<>" -> !lhsval.equals(rhsval);
         case ">" -> lhsval.compareTo(rhsval) > 0;
         case ">=" ->  lhsval.compareTo(rhsval) >= 0;
         case "<" -> lhsval.compareTo(rhsval) < 0;
         case "<=" ->  lhsval.compareTo(rhsval) <= 0;
         default -> false;
      };
   }
   
   /**
    * Calculate the extent to which selecting on the term reduces 
    * the number of records output by a query.
    * For example if the reduction factor is 2, then the
    * term cuts the size of the output in half.
    * @param p the query's plan
    * @return the integer reduction factor.
    */
   public int reductionFactor(Plan p) {
      // a term comparing two constants keeps all records or none
      if (!lhs.isFieldName() && !rhs.isFieldName())
         return isSatisfied(null) ? 1 : Integer.MAX_VALUE;
      // a range comparison is assumed to keep a third of the records,
      // and an inequality (nearly) all of them
      if (opr.equals("!=") || opr.equals("<>"))
         return 1;
      if (!opr.equals("="))
         return 3;
      String lhsName, rhsName;
      if (lhs.isFieldName() && rhs.isFieldName()) {
         lhsName = lhs.asFieldName();
         rhsName = rhs.asFieldName();
         return Math.max(p.distinctValues(lhsName),
                         p.distinctValues(rhsName));
      }
      if (lhs.isFieldName()) {
         lhsName = lhs.asFieldName();
         return p.distinctValues(lhsName);
      }
      if (rhs.isFieldName()) {
         rhsName = rhs.asFieldName();
         return p.distinctValues(rhsName);
      }
      // otherwise, the term equates constants
      if (lhs.asConstant().equals(rhs.asConstant()))
         return 1;
      else
         return Integer.MAX_VALUE;
   }
   
   /**
    * Determine if this term is of the form "F=c"
    * where F is the specified field and c is some constant.
    * If so, the method returns that constant.
    * If not, the method returns null.
    * @param fldname the name of the field
    * @return either the constant or null
    */
   public Constant equatesWithConstant(String fldname) {
      if (!opr.equals("=")) return null;
      if (lhs.isFieldName() &&
          lhs.asFieldName().equals(fldname) &&
          !rhs.isFieldName())
         return rhs.asConstant();
      else if (rhs.isFieldName() &&
               rhs.asFieldName().equals(fldname) &&
               !lhs.isFieldName())
         return lhs.asConstant();
      else
         return null;
   }
   
   /**
    * Determine if this term is of the form "F1=F2"
    * where F1 is the specified field and F2 is another field.
    * If so, the method returns the name of that field.
    * If not, the method returns null.
    * @param fldname the name of the field
    * @return either the name of the other field, or null
    */
   public String equatesWithField(String fldname) {
      if (!opr.equals("=")) return null;
      if (lhs.isFieldName() &&
          lhs.asFieldName().equals(fldname) &&
          rhs.isFieldName())
         return rhs.asFieldName();
      else if (rhs.isFieldName() &&
               rhs.asFieldName().equals(fldname) &&
               lhs.isFieldName())
         return lhs.asFieldName();
      else
         return null;
   }
   
   /**
    * Return true if both of the term's expressions
    * apply to the specified schema.
    * @param sch the schema
    * @return true if both expressions apply to the schema
    */
   public boolean appliesTo(Schema sch) {
      return lhs.appliesTo(sch) && rhs.appliesTo(sch);
   }
   
   public String toString() {
      return lhs.toString() + opr + rhs.toString();
   }

   /**
    * Returns a copy of this term with its field names renamed.
    * @param rename maps a field name to its new name
    * @return the renamed term
    */
   public Term rename(Function<String,String> rename) {
      return new Term(lhs.rename(rename), rhs.rename(rename), opr);
   }

   /**
    * Returns the comparison operator of this term, e.g. "=" or "<=".
    */
   public String operator() {
      return opr;
   }

   public Expression lhs() {
      return lhs;
   }

   public Expression rhs() {
      return rhs;
   }

   /**
    * Returns true if this term is "f1 = f2" (in either order),
    * for the two specified fields.
    */
   public boolean equatesFields(String f1, String f2) {
      return opr.equals("=") && lhs.isFieldName() && rhs.isFieldName()
            && ((lhs.asFieldName().equals(f1) && rhs.asFieldName().equals(f2))
             || (lhs.asFieldName().equals(f2) && rhs.asFieldName().equals(f1)));
   }

   /**
    * Returns true if this term is "f = c" (in either order),
    * for the specified field and constant.
    */
   public boolean equatesFieldWith(String fldname, Constant val) {
      if (!opr.equals("="))
         return false;
      return (lhs.isFieldName() && lhs.asFieldName().equals(fldname) && !rhs.isFieldName() && rhs.asConstant().equals(val))
          || (rhs.isFieldName() && rhs.asFieldName().equals(fldname) && !lhs.isFieldName() && lhs.asConstant().equals(val));
   }
}
