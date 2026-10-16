package simpledb.parse;

/**
 * A runtime exception indicating that a statement is well formed
 * but invalid, e.g. it names a table or field that does not exist.
 * It is a kind of BadSyntaxException, so callers that reject bad
 * statements need not distinguish the two, but it carries a message
 * explaining the problem.
 */
@SuppressWarnings("serial")
public class BadQueryException extends BadSyntaxException {
   public BadQueryException(String message) {
      super(message);
   }
}
