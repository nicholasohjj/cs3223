package simpledb.test;

import java.util.Random;
import simpledb.plan.Planner;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Creates a larger version of the student database, for timing
 * experiments: the same five tables and indexes as CreateStudentDB,
 * filled with generated records (a fixed random seed, so every run
 * builds the same data).
 * <p>
 * Usage: CreateLargeStudentDB [dbname [students]]; the default is
 * "studentdb_large" with 3000 students.  The other tables scale with it:
 * 5 enrollments per student, a section per 2 students, a course per
 * 10 students and 20 departments.
 */
public class CreateLargeStudentDB {
   public static void main(String[] args) {
      String dbname = (args.length > 0) ? args[0] : "studentdb_large";
      int students = (args.length > 1) ? Integer.parseInt(args[1]) : 3000;
      create(dbname, students);
   }

   public static void create(String dbname, int students) {
      int depts = 20, courses = students / 10, sections = students / 2, enrolls = students * 5;
      String[] grades = {"A", "A-", "B+", "B", "B-", "C+", "C", "D", "F"};
      Random rnd = new Random(3223);
      SimpleDB db = new SimpleDB(dbname);
      Planner planner = db.planner();
      Transaction tx = db.newTx();

      planner.executeUpdate("create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)", tx);
      planner.executeUpdate("create table DEPT(DId int, DName varchar(8))", tx);
      planner.executeUpdate("create table COURSE(CId int, Title varchar(20), DeptId int)", tx);
      planner.executeUpdate("create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)", tx);
      planner.executeUpdate("create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))", tx);
      // the same indexes as CreateStudentDB
      planner.executeUpdate("create index student_majorid on STUDENT (MajorId) using hash", tx);
      planner.executeUpdate("create index student_sid on STUDENT (SId) using btree", tx);
      planner.executeUpdate("create index enroll_studentid on ENROLL (StudentId) using btree", tx);

      for (int i=1; i<=depts; i++)
         planner.executeUpdate("insert into DEPT(DId, DName) values (" + i + ", 'dept" + i + "')", tx);
      for (int i=1; i<=courses; i++)
         planner.executeUpdate("insert into COURSE(CId, Title, DeptId) values (" + i + ", 'course" + i + "', "
               + (1 + rnd.nextInt(depts)) + ")", tx);
      for (int i=1; i<=sections; i++)
         planner.executeUpdate("insert into SECTION(SectId, CourseId, Prof, YearOffered) values (" + i + ", "
               + (1 + rnd.nextInt(courses)) + ", 'prof" + (1 + rnd.nextInt(100)) + "', " + (2015 + rnd.nextInt(10)) + ")", tx);
      for (int i=1; i<=students; i++)
         planner.executeUpdate("insert into STUDENT(SId, SName, MajorId, GradYear) values (" + i + ", 'stud" + i + "', "
               + (1 + rnd.nextInt(depts)) + ", " + (2019 + rnd.nextInt(8)) + ")", tx);
      for (int i=1; i<=enrolls; i++)
         planner.executeUpdate("insert into ENROLL(EId, StudentId, SectionId, Grade) values (" + i + ", "
               + (1 + rnd.nextInt(students)) + ", " + (1 + rnd.nextInt(sections)) + ", '" + grades[rnd.nextInt(grades.length)] + "')", tx);
      tx.commit();
      System.out.println("Created " + dbname + ": " + depts + " depts, " + courses + " courses, " + sections
            + " sections, " + students + " students, " + enrolls + " enrollments.");
   }
}
