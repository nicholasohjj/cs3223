# SimpleDB+ demo script

Small database: `test/CreateStudentDB` (9 students, 3 depts, 6 courses,
5 sections, 6 enrolments). Indexes: hash on `student(majorid)`, B+-tree on
`student(sid)` and `enroll(studentid)`.

## Setup

Compile, then build a fresh database in an empty directory (SimpleDB opens
`studentdb` relative to the working directory). Run these from the repo root:

```
REPO=$(pwd)
javac -nowarn -d out/demo $(find src -name '*.java')
mkdir -p /tmp/simpledb-demo && cd /tmp/simpledb-demo && rm -rf studentdb
java -cp "$REPO"/out/demo simpledb.test.CreateStudentDB
java -cp "$REPO"/out/demo simpledb.test.SimpleIJ
```

Every query prints its plan before its rows. To show a plan without running
the query, prefix it with `explain`. Each statement must be on one line.

## (a) B+-tree and hash indexes

```
select sid, sname, gradyear from student where sid = 5
select sid, sname from student where majorid = 20
select sname, grade from student, enroll where sid = studentid
```

1. IndexSelect on the B+-tree `student_sid`.
2. IndexSelect on the hash index `student_majorid`.
3. IndexJoin probing a B+-tree.

## (b) Duplicates, then distinct

```
select dname from student, dept where majorid = did
select distinct dname from student, dept where majorid = did
```

The first query returns 9 rows (compsci x3, math x4, drama x2). The second
returns 3, through a sort-based Distinct.

## (c) 3-way joins with order by (equality and non-equality)

```
select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid
select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid order by dname, sname desc
select sname, title, gradyear from student, dept, course where majorid = did and did = deptid and gradyear >= 2021 order by gradyear desc, sname
select sname, prof, yearoffered from student, enroll, section where sid = studentid and sectionid <> sectid and yearoffered < 2019 order by sname, prof
```

1. Two index joins: hash on `majorid`, then B+-tree on `studentid`.
2. The same joins with a Sort node added, using mixed asc/desc keys.
3. Hash joins. The `>=` selection is pushed below the join.
4. A `<>` join term, so a NestedLoopsJoin is used.

## (d) Aggregates

```
select count(sid), min(gradyear), max(gradyear), avg(gradyear) from student
select majorid, count(sid), avg(gradyear) from student group by majorid
select dname, count(eid), max(grade) from student, dept, enroll where majorid = did and sid = studentid group by dname order by dname desc
```

## (e) Bonus features

Double type:
```
create table item(iname varchar(10), price double, qty int)
insert into item(iname, price, qty) values ('pen', 1.5, 10)
insert into item(iname, price, qty) values ('book', 12.25, 3)
insert into item(iname, price, qty) values ('bag', 30.0, 1)
insert into item(iname, price, qty) values ('ink', 1.5, 7)
select iname, price from item where price > 1.75 order by price desc
select sum(price), avg(price), min(price), count(qty) from item
```

Aliases and qualified names (self-join):
```
select s.sname, t.sname from student s, student t where s.majorid = t.majorid and s.sid < t.sid order by s.sname
```

Set operations:
```
select sname from student where majorid = 10 union select sname from student where gradyear = 2020
select sname from student where majorid = 20 intersect select sname from student where gradyear = 2020
select majorid from student except all select did from dept
```

Query validation (each of these returns an error message):
```
select nosuchfield from student
select sname from student where sname = 3
select sname from student where gradyear > 2020 order by sname garbage
```

Cost-based optimizer, and forcing a join method. Restart SimpleIJ with one
of these flags (`merge` can also be `hash`, `nested` or `index`) and
`explain` the same query to compare the plans:
```
java -Dsimpledb.planner=cost -cp "$REPO"/out/demo simpledb.test.SimpleIJ
java -Dsimpledb.joins=merge -cp "$REPO"/out/demo simpledb.test.SimpleIJ
explain select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid order by dname
```

Every table here fits in one block. That is why the cost-based planner may
take a product of `enroll` and `dept` (6 x 3 rows) before a hash join: on
one-block inputs, that is as cheap as any other order.

## (f) Query plans

Every query prints its plan. `explain <query>` prints the plan only.
