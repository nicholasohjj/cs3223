package simpledb.parse;

import java.lang.reflect.Array;
import java.util.*;
import java.io.*;

/**
 * The lexical analyzer.
 * @author Edward Sciore
 */
public class Lexer {
   private Collection<String> keywords;
   private StreamTokenizer tok;
   
   /**
    * Creates a new lexical analyzer for SQL statement s.
    * @param s the SQL statement
    */
   public Lexer(String s) {
      initKeywords();
      tok = new StreamTokenizer(new StringReader(s));
      // "." is a number constituent, so decimals such as 3.5 are single
      // number tokens, and qualified names such as s.sid are single words
      tok.ordinaryChar('<');
      tok.ordinaryChar('>');
      tok.ordinaryChar('=');
      tok.ordinaryChar('!');
      tok.wordChars('_', '_'); //allow "_" in identifiers
      tok.lowerCaseMode(true); //ids and keywords are converted
      nextToken();
   }
   
//Methods to check the status of the current token
   
   /**
    * Returns true if the current token is
    * the specified delimiter character.
    * @param d a character denoting the delimiter
    * @return true if the delimiter is the current token
    */
   public boolean matchDelim(char d) {
      return d == (char)tok.ttype;
   }
   
   /**
    * Returns true if the current token is an integer.
    * @return true if the current token is an integer
    */
   public boolean matchIntConstant() {
      return tok.ttype == StreamTokenizer.TT_NUMBER && tok.nval == Math.rint(tok.nval);
   }

   /**
    * Returns true if the current token is a number with a
    * fractional part, such as 3.5.
    * @return true if the current token is a double
    */
   public boolean matchDoubleConstant() {
      return tok.ttype == StreamTokenizer.TT_NUMBER && tok.nval != Math.rint(tok.nval);
   }
   
   /**
    * Returns true if the current token is a string.
    * @return true if the current token is a string
    */
   public boolean matchStringConstant() {
      return '\'' == (char)tok.ttype;
   }
   
   /**
    * Returns true if the current token is the specified keyword.
    * @param w the keyword string
    * @return true if that keyword is the current token
    */
   /**
    * Returns true if the whole statement has been read.
    * @return true if there are no more tokens
    */
   public boolean matchEnd() {
      return tok.ttype == StreamTokenizer.TT_EOF;
   }

   /**
    * Returns the current token as text, for error messages.
    * @return a description of the current token
    */
   public String currentToken() {
      if (tok.ttype == StreamTokenizer.TT_WORD || tok.ttype == '\'')
         return tok.sval;
      if (tok.ttype == StreamTokenizer.TT_NUMBER)
         return Double.toString(tok.nval).replaceAll("\\.0$", "");
      return String.valueOf((char) tok.ttype);
   }

   public boolean matchKeyword(String w) {
      return tok.ttype == StreamTokenizer.TT_WORD && tok.sval.equals(w);
   }

   public boolean matchOpr() {
      char c = (char) tok.ttype;
      return c == '<' || c == '>' || c == '=' || c == '!';
   }

   /**
    * Returns true if the current token is a legal identifier.
    * @return true if the current token is an identifier
    */
   public boolean matchId() {
      return  tok.ttype==StreamTokenizer.TT_WORD && !keywords.contains(tok.sval);
   }
   
//Methods to "eat" the current token
   
   /**
    * Throws an exception if the current token is not the
    * specified delimiter. 
    * Otherwise, moves to the next token.
    * @param d a character denoting the delimiter
    */
   public void eatDelim(char d) {
      if (!matchDelim(d))
         throw new BadSyntaxException();
      nextToken();
   }

   public void eatEquals() {
      String opr = eatOpr();
      if (!opr.equals("="))
         throw new BadSyntaxException();
   }

   public String eatOpr() {
      if (!matchOpr())
         throw new BadSyntaxException();
      char first = (char) tok.ttype;
      nextToken();
      if ((first == '<' || first == '>' || first == '!') && (char) tok.ttype == '=') {
         nextToken();
         return "" + first + "=";
      }
      if (first == '<' && (char) tok.ttype == '>') {
         nextToken();
         return "<>";
      }
      if (first == '!')
         throw new BadSyntaxException();
      return "" + first;
   }
   
   /**
    * Throws an exception if the current token is not 
    * an integer. 
    * Otherwise, returns that integer and moves to the next token.
    * @return the integer value of the current token
    */
   public int eatIntConstant() {
      if (!matchIntConstant())
         throw new BadSyntaxException();
      int i = (int) tok.nval;
      nextToken();
      return i;
   }
   
   /**
    * Throws an exception if the current token is not 
    * a string. 
    * Otherwise, returns that string and moves to the next token.
    * @return the string value of the current token
    */
   /**
    * Throws an exception if the current token is not
    * a double.
    * Otherwise, returns that double and moves to the next token.
    * @return the double value of the current token
    */
   public double eatDoubleConstant() {
      if (!matchDoubleConstant())
         throw new BadSyntaxException();
      double d = tok.nval;
      nextToken();
      return d;
   }
   
   public String eatStringConstant() {
      if (!matchStringConstant())
         throw new BadSyntaxException();
      String s = tok.sval; //constants are not converted to lower case
      nextToken();
      return s;
   }
   
   /**
    * Throws an exception if the current token is not the
    * specified keyword. 
    * Otherwise, moves to the next token.
    * @param w the keyword string
    */
   public void eatKeyword(String w) {
      if (!matchKeyword(w))
         throw new BadSyntaxException();
      nextToken();
   }
   
   /**
    * Throws an exception if the current token is not 
    * an identifier. 
    * Otherwise, returns the identifier string 
    * and moves to the next token.
    * @return the string value of the current token
    */
   public String eatId() {
      if (!matchId())
         throw new BadSyntaxException();
      String s = tok.sval;
      nextToken();
      return s;
   }
   
   private void nextToken() {
      try {
         tok.nextToken();
      }
      catch(IOException e) {
         throw new BadSyntaxException();
      }
   }
   
   private void initKeywords() {
      keywords = Arrays.asList("select", "from", "where", "and",
                               "insert", "into", "values", "delete", "update", "set", 
                               "create", "table", "int", "varchar", "view", "as", "index", "on", "order", "by", "asc", "desc", "group",
                               "using", "hash", "btree", "distinct", "double", "float",
                               "union", "intersect", "except", "all");
   }
}