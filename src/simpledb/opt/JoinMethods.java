package simpledb.opt;

import java.util.*;

/**
 * The join methods the optimizers may use: "index", "hash", "merge"
 * and "nested" (nested loops).  All are allowed by default; a subset
 * can be chosen with the system property simpledb.joins (e.g.
 * -Dsimpledb.joins=hash,merge) or with allow(), e.g. to compare plans
 * that use different join algorithms.
 * <p>
 * A join is always possible: when none of the allowed methods applies
 * (e.g. only "merge" is allowed and the join predicate is "a &lt; b"),
 * a nested-loops join is used.
 */
public class JoinMethods {
   public static final String INDEX = "index", HASH = "hash", MERGE = "merge", NESTED = "nested";
   private static final List<String> ALL = List.of(INDEX, HASH, MERGE, NESTED);
   private static Set<String> allowed = parse(System.getProperty("simpledb.joins", String.join(",", ALL)));

   private JoinMethods() {}

   /**
    * Allows only the specified join methods (all of them, if none are given).
    * @param methods some of "index", "hash", "merge" and "nested"
    */
   public static synchronized void allow(String... methods) {
      allowed = parse(methods.length == 0 ? String.join(",", ALL) : String.join(",", methods));
   }

   public static synchronized boolean isAllowed(String method) {
      return allowed.contains(method);
   }

   public static synchronized String describe() {
      return String.join(", ", allowed);
   }

   private static Set<String> parse(String list) {
      Set<String> result = new LinkedHashSet<>();
      for (String m : list.split(",")) {
         m = m.trim().toLowerCase();
         if (!ALL.contains(m))
            throw new IllegalArgumentException("unknown join method " + m + "; use " + String.join(", ", ALL));
         result.add(m);
      }
      return result;
   }
}
