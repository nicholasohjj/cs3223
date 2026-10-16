package simpledb.metadata;

import java.util.*;

/**
 * A StatInfo object holds three pieces of 
 * statistical information about a table:
 * the number of blocks, the number of records,
 * and the number of distinct values for each field. 
 * @author Edward Sciore
 */
public class StatInfo {
   private int numBlocks;
   private int numRecs;
   private Map<String,Integer> distinct = new HashMap<>();
   
   /**
    * Create a StatInfo object.
    * Note that the number of distinct values is not
    * passed into the constructor.
    * The object fakes this value.
    * @param numblocks the number of blocks in the table
    * @param numrecs the number of records in the table
    */
   public StatInfo(int numblocks, int numrecs) {
      this.numBlocks = numblocks;
      this.numRecs   = numrecs;
   }
   
   /**
    * Return the estimated number of blocks in the table.
    * @return the estimated number of blocks in the table
    */
   public int blocksAccessed() {
      return numBlocks;
   }
   
   /**
    * Return the estimated number of records in the table.
    * @return the estimated number of records in the table
    */
   public int recordsOutput() {
      return numRecs;
   }
   
   /**
    * Return the estimated number of distinct values
    * for the specified field.
    * This estimate is a complete guess, because doing something
    * reasonable is beyond the scope of this system.
    * @param fldname the name of the field
    * @return a guess as to the number of distinct field values
    */
   /**
    * Create a StatInfo object that also holds the number of
    * distinct values of each field.
    * @param distinct maps each field to its number of distinct values
    */
   public StatInfo(int numblocks, int numrecs, Map<String,Integer> distinct) {
      this(numblocks, numrecs);
      this.distinct = distinct;
   }

   /**
    * Return the number of distinct values of the specified field,
    * as counted when the statistics were gathered; for a field
    * that was not counted, it is estimated as 1 + R/3.
    * @param fldname the name of the field
    * @return the number of distinct values of the field
    */
   public int distinctValues(String fldname) {
      Integer v = distinct.get(fldname);
      return (v != null) ? Math.max(1, v) : 1 + (numRecs / 3);
   }
}
