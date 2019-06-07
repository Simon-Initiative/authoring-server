/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.cmu.oli.content.reportingdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.logging.Logging;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.sql.*;

/**
 * Runs queries against the reporting database (db-03)
 */
@Stateless
public class DbConnector {

    @Inject
    @Logging
    Logger log;

    // Can be extended to use `databaseResultsToTsv`
    public JsonArray readDatabase(final String query) throws SQLException {
        String dbPath = System.getenv().get("dataset_db");

        try (Connection connection = DriverManager.getConnection(dbPath);
                Statement statement = connection.createStatement()) {
            Class.forName("com.mysql.jdbc.Driver");

            // Terminate if query takes longer than 15 minutes
            statement.setQueryTimeout(60 * 15);
            log.info("Executing query: " + query);

            ResultSet result = statement.executeQuery(query);
            log.info("Query successful");
            // Hardcoded to return results in JSON form
            JsonArray jsonResult = databaseResultsToJson(result);
            return jsonResult;
        } catch (SQLException | ClassNotFoundException e) {
            log.error("SQL Exception in readDatabase.", e);
            throw new SQLException("There was an error when reading from the course database.");
        }
    }

    private JsonArray databaseResultsToJson(final ResultSet resultSet) throws SQLException {
        JsonArray json = new JsonArray();
        ResultSetMetaData metadata = resultSet.getMetaData();
        while (resultSet.next()) {
            int numColumns = metadata.getColumnCount();
            JsonObject obj = new JsonObject();
            for (int i = 1; i <= numColumns; i++) {
                String columnName = metadata.getColumnName(i);
                obj.addProperty(columnName, resultSet.getObject(columnName).toString());
            }
            json.add(obj);
        }
        return json;
    }

    // Allows returning tab-separated results instead of in JSON
    private String databaseResultsToTsv(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        log.info("Table: " + metadata.getTableName(1));
        int columnCount = metadata.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metadata.getColumnName(i);
            sb.append(columnName);
            if (i != columnCount) {
                sb.append("\t");
            }
        }
        sb.append("\n");
        log.info("headers " + sb.toString());

        while (resultSet.next()) {
            for (int i = 1; i <= columnCount; i++) {
                String columnValue = resultSet.getString(i);
                sb.append(columnValue);
                if (i != columnCount) {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}