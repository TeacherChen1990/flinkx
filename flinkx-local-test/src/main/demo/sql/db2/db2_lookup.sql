-- {"id":100,"name":"lb james阿道夫","money":293.899778,"dateone":"2020-07-30 10:08:22","age":"33","datethree":"2020-07-30 10:08:22.123","datesix":"2020-07-30 10:08:22.123456","datenigth":"2020-07-30 10:08:22.123456789","dtdate":"2020-07-30","dttime":"10:08:22"}
CREATE TABLE source
(
    id        INT,
    name      STRING,
    money     decimal,
    dateone   timestamp,
    age       bigint,
    datethree timestamp,
    datesix   timestamp(6),
    datenigth timestamp(9),
    dtdate    date,
    dttime    time,
    PROCTIME AS PROCTIME()
) WITH (
      'connector' = 'kafka-x'
      ,'topic' = 'da'
      ,'properties.bootstrap.servers' = 'kudu1:9092'
      ,'properties.group.id' = 'luna_g'
      ,'scan.startup.mode' = 'earliest-offset'
      -- ,'scan.startup.mode' = 'latest-offset'
      ,'format' = 'json'
      ,'json.timestamp-format.standard' = 'SQL'
      );

/*CREATE TABLE flink_dim (
                           id int DEFAULT NULL,
                           name varchar(255) DEFAULT NULL,
                           money decimal(9,6) DEFAULT NULL,
                           age bigint DEFAULT NULL,
                           datethree timestamp DEFAULT NULL,
                           datesix timestamp  DEFAULT NULL,
                           phone bigint DEFAULT NULL,
                           wechat varchar(255) DEFAULT NULL,
                           income decimal(9,6) DEFAULT NULL,
                           birthday timestamp DEFAULT NULL,
                           dtdate date DEFAULT NULL,
                           dttime time DEFAULT NULL,
                           today date DEFAULT NULL,
                           timecurrent time DEFAULT NULL,
                           dateone timestamp  DEFAULT NULL,
                           aboolean smallint DEFAULT NULL,
                           adouble double DEFAULT NULL,
                           afloat float DEFAULT NULL,
                           achar char(1) DEFAULT NULL,
                           abinary blob DEFAULT NULL,
                           atinyint smallint DEFAULT NULL
)*/
--INSERT INTO flink_out (id, name, money, age, datethree, datesix, phone, wechat, income, birthday, dtdate, dttime, today, timecurrent, dateone) VALUES (100, 'kobe james阿道夫', 30.230000, 30, '2020-03-03 03:03:03', '2020-06-06 06:06:06', 11111111111111, '这是我的wechat', 23.120000, '2020-10-10 10:10:10', '2020-12-12', '12:12:12', '2020-10-10', '10:10:10', '2020-01-01 01:01:01');
--INSERT INTO test.flink_out (id, name, money, age, datethree, datesix, phone, wechat, income, birthday, dtdate, dttime, today, timecurrent, dateone) VALUES (100, 'kobe james阿道夫', 30.230000, 30, '2020-03-03 03:03:03', '2020-06-06 06:06:06', 11111111111111, '这是我的wechat', 23.120000, '2020-10-10 10:10:10', '2020-12-12', '12:12:12', '2020-10-10', '10:10:10', '2020-01-01 01:01:01');

CREATE TABLE side
(
    id          int,
    name        varchar,
    money       decimal,
    dateone     timestamp,
    age         bigint,
    datethree   timestamp,
    datesix     timestamp,
    phone       bigint,
    wechat      varchar,
    income      decimal,
    birthday    timestamp,
    dtdate      date,
    dttime      time,
    today       date,
    timecurrent time,
    aboolean    smallint ,
    adouble     double,
    afloat      double,
    achar       char,
    abinary     BYTES,
    atinyint    smallint ,
    PRIMARY KEY (id) NOT ENFORCED
) WITH (
      'connector' = 'db2-x',
      'url' = 'jdbc:db2://172.16.101.246:50002/DT_TEST',
      'table-name' = 'flink_dim',
      'username' = 'db2inst1',
      'password' = 'dtstack1',
      'lookup.cache-type' = 'all',
	  'lookup.parallelism' = '2'
      );

CREATE TABLE sink
(
    id          int,
    name        varchar,
    money       decimal,
    dateone     timestamp,
    age         bigint,
    datethree   timestamp,
    datesix     timestamp,
    phone       bigint,
    wechat      varchar,
    income      decimal,
    birthday    timestamp,
    dtdate      date,
    dttime      time,
    today       date,
    timecurrent time,
    aboolean    smallint,
    adouble     double,
    afloat      double,
    achar       char,
    abinary     BYTES,
    atinyint    smallint
) WITH (
      'connector' = 'stream-x',
	  'sink.parallelism' = '1'
      );

create
TEMPORARY view view_out
  as
select u.id
     , u.name
     , u.money
     , u.dateone
     , u.age
     , u.datethree
     , u.datesix
     , s.phone
     , s.wechat
     , s.income
     , s.birthday
     , u.dtdate
     , u.dttime
     , s.today
     , s.timecurrent
     , s.aboolean
     , s.adouble
     , s.afloat
     , s.achar
     , s.abinary
     , s.atinyint
from source u
         left join side FOR SYSTEM_TIME AS OF u.PROCTIME AS s
                   on u.id = s.id;

insert into sink
select *
from view_out;
