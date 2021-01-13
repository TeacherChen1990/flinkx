/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dtstack.flinkx.metadata.inputformat;

import com.dtstack.flinkx.inputformat.BaseRichInputFormat;
import com.dtstack.flinkx.metadata.MetaDataCons;
import com.dtstack.flinkx.metadata.util.ConnUtil;
import com.dtstack.flinkx.util.ExceptionUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.core.io.InputSplit;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author : tiezhu
 * @date : 2020/3/20
 */
public abstract class BaseMetadataInputFormat extends BaseRichInputFormat {

    private static final long serialVersionUID = 1L;

    protected String dbUrl;

    protected String username;

    protected String password;

    protected String driverName;

    protected boolean queryTable;

    /**
     * 记录任务参数传入的database和表名
     */
    protected List<Map<String, Object>> dbTableList;

    /**
     * 存放所有需要查询的表的名字
     */
    protected List<Object> tableList;

    /**
     * 记录当前查询的表所在list中的位置
     */
    protected int start;

    protected static transient ThreadLocal<Connection> connection = new ThreadLocal<>();

    protected static transient ThreadLocal<Statement> statement = new ThreadLocal<>();

    protected static transient ThreadLocal<String> currentDb = new ThreadLocal<>();

    protected static transient ThreadLocal<Iterator<Object>> tableIterator = new ThreadLocal<>();

    @Override
    protected void openInternal(InputSplit inputSplit) throws IOException {
        try {
            connection.set(getConnection());
            statement.set(connection.get().createStatement());
            currentDb.set(((MetadataInputSplit) inputSplit).getDbName());
            switchDatabase(currentDb.get());
            tableList = ((MetadataInputSplit) inputSplit).getTableList();
            if (CollectionUtils.isEmpty(tableList)) {
                tableList = showTables();
                queryTable = true;
            }
            LOG.info("current database = {}, tableSize = {}, tableList = {}",currentDb.get(), tableList.size(), tableList);
            tableIterator.set(tableList.iterator());
            start = 0;
            init();
        } catch (ClassNotFoundException e) {
            LOG.error("could not find suitable driver, e={}", ExceptionUtil.getErrorMessage(e));
            throw new IOException(e);
        } catch (SQLException e){
            LOG.error("获取table列表异常, dbUrl = {}, username = {}, inputSplit = {}, e = {}", dbUrl, username, inputSplit, ExceptionUtil.getErrorMessage(e));
            tableList = new LinkedList<>();
        }
        LOG.info("curentDb = {}, tableList = {}", currentDb.get(), tableList);
        tableIterator.set(tableList.iterator());
    }

    /**
     * 按照database进行划分，可能与channel数不同
     * @param splitNumber 最小分片数
     * @return 分片
     */
    @Override
    @SuppressWarnings("unchecked")
    protected InputSplit[] createInputSplitsInternal(int splitNumber) {
        InputSplit[] inputSplits = new MetadataInputSplit[dbTableList.size()];
        for (int index = 0; index < dbTableList.size(); index++) {
            Map<String, Object> dbTables = dbTableList.get(index);
            String dbName = MapUtils.getString(dbTables, MetaDataCons.KEY_DB_NAME);
            if(StringUtils.isNotEmpty(dbName)){
                List<Object> tables = (List<Object>)dbTables.get(MetaDataCons.KEY_TABLE_LIST);
                inputSplits[index] = new MetadataInputSplit(splitNumber, dbName, tables);
            }
        }
        return inputSplits;
    }

    @Override
    protected Row nextRecordInternal(Row row) throws IOException{
        Map<String, Object> metaData = new HashMap<>(16);
        metaData.put(MetaDataCons.KEY_OPERA_TYPE, MetaDataCons.DEFAULT_OPERA_TYPE);

        String tableName = (String) tableIterator.get().next();
        metaData.put(MetaDataCons.KEY_SCHEMA, currentDb.get());
        metaData.put(MetaDataCons.KEY_TABLE, tableName);
        try {
            metaData.putAll(queryMetaData(tableName));
            metaData.put(MetaDataCons.KEY_QUERY_SUCCESS, true);
        } catch (Exception e) {
            metaData.put(MetaDataCons.KEY_QUERY_SUCCESS, false);
            metaData.put(MetaDataCons.KEY_ERROR_MSG, ExceptionUtil.getErrorMessage(e));
            LOG.error(ExceptionUtil.getErrorMessage(e));
        }
        start++;
        return Row.of(metaData);
    }

    @Override
    public boolean reachedEnd() {
        return !tableIterator.get().hasNext();
    }

    @Override
    protected void closeInternal() throws IOException {
        tableIterator.remove();
        Statement st = statement.get();
        if (null != st) {
            try {
                st.close();
                statement.remove();
            } catch (SQLException e) {
                LOG.error("close statement failed, e = {}", ExceptionUtil.getErrorMessage(e));
                throw new IOException("close statement failed", e);
            }
        }

        currentDb.remove();
        Connection conn = connection.get();
        if (null != conn) {
            try {
                conn.close();
                connection.remove();
            } catch (SQLException e) {
                LOG.error("close database connection failed, e = {}", ExceptionUtil.getErrorMessage(e));
                throw new IOException("close database connection failed", e);
            }
        }
    }

    @Override
    public void closeInputFormat() throws IOException {
        super.closeInputFormat();
    }

    /**
     * 创建数据库连接
     */
    public Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName(driverName);
        return ConnUtil.getConnection(dbUrl, username, password);
    }

    /**
     * 查询当前数据库下所有的表
     *
     * @return  表名列表
     * @throws SQLException 异常
     */
    protected abstract List<Object> showTables() throws SQLException;

    /**
     * 切换当前database
     *
     * @param databaseName 数据库名
     * @throws SQLException 异常
     */
    protected abstract void switchDatabase(String databaseName) throws SQLException;

    /**
     * 根据表名查询元数据信息
     * @param tableName 表名
     * @return 元数据信息
     * @throws SQLException 异常
     */
    protected abstract Map<String, Object> queryMetaData(String tableName) throws SQLException;

    /**
     * 将数据库名，表名，列名字符串转为对应的引用，如：testTable -> `testTable`
     * @param name 入参
     * @return 返回数据库名，表名，列名的引用
     */
    protected abstract String quote(String name);

    /**
     * 提供子类对新增成员变量初始化的接口
     */
    protected void init() throws SQLException {}

    /**
     * 对执行sql方法的包装，增加日志打印和异常处理
     * @param sql 原始sql语句
     * @param statement 原始的statement
     */
    protected ResultSet executeQuery0(String sql, Statement statement){
        ResultSet resultSet = null;
        if(StringUtils.isNotBlank(sql)){
            LOG.info("execute SQL : {}", sql);
            try{
                if(statement!=null){
                    resultSet = statement.executeQuery(sql);
                }
            }catch (SQLException e){
                LOG.error("execute SQL failed : {}", ExceptionUtil.getErrorMessage(e));
            }

        }
        return resultSet;
    }

}