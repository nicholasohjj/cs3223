package simpledb.query;

import static java.sql.Types.*;

/**
 * The class that denotes values stored in the database:
 * an integer, a double, or a string.
 * Integers and doubles are both numeric, and compare with
 * each other by value, so that 2 = 2.0 and 2 < 2.5.
 * @author Edward Sciore
 */
public class Constant implements Comparable<Constant> {
   private Integer ival = null;
   private Double  dval = null;
   private String  sval = null;

   public Constant(Integer ival) {
      this.ival = ival;
   }

   public Constant(Double dval) {
      this.dval = dval;
   }

   public Constant(String sval) {
      this.sval = sval;
   }

   public int asInt() {
      return ival;
   }

   /**
    * Returns the value as a double; an integer is widened.
    * @return the numeric value
    */
   public double asDouble() {
      return (ival != null) ? ival : dval;
   }

   public String asString() {
      return sval;
   }

   /**
    * Returns the SQL type of the value: INTEGER, DOUBLE or VARCHAR.
    * @return the java.sql.Types code of the value
    */
   public int type() {
      return (ival != null) ? INTEGER : (dval != null) ? DOUBLE : VARCHAR;
   }

   public boolean isNumeric() {
      return sval == null;
   }

   public boolean equals(Object obj) {
      if (!(obj instanceof Constant))
         return false;
      Constant c = (Constant) obj;
      if (ival != null && c.ival != null)
         return ival.equals(c.ival);
      if (isNumeric() && c.isNumeric())
         return asDouble() == c.asDouble();
      return sval != null && sval.equals(c.sval);
   }

   public int compareTo(Constant c) {
      if (ival != null && c.ival != null)
         return ival.compareTo(c.ival);
      if (isNumeric() && c.isNumeric())
         return Double.compare(asDouble(), c.asDouble());
      if (sval != null && c.sval != null)
         return sval.compareTo(c.sval);
      throw new IllegalArgumentException("cannot compare " + this + " with " + c);
   }

   /**
    * A double with an integer value hashes like that integer,
    * since the two are equal; this keeps hash indexes and
    * hash joins consistent across the numeric types.
    */
   public int hashCode() {
      if (ival != null)
         return ival.hashCode();
      if (dval != null) {
         double d = dval;
         if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
            return Integer.hashCode((int) d);
         return Double.hashCode(d);
      }
      return sval.hashCode();
   }

   public String toString() {
      return (ival != null) ? ival.toString() : (dval != null) ? dval.toString() : sval;
   }
}
