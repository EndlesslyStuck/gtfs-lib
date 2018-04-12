package com.conveyal.gtfs.loader;

import com.conveyal.gtfs.model.Entity;
import com.conveyal.gtfs.storage.StorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.dbutils.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.conveyal.gtfs.loader.JdbcGtfsLoader.INSERT_BATCH_SIZE;

/**
 * This wraps a single database table and provides methods to modify GTFS entities.
 */
public class JdbcTableWriter implements TableWriter {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcTableWriter.class);
    private final DataSource dataSource;
    private final Table specTable;
    private final String tablePrefix;
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Connection connection;

    public JdbcTableWriter(Table table, DataSource datasource, String namespace) {
        this(table, datasource, namespace, null);
    }

    /**
     * Enum containing available methods for updating in SQL.
     */
    private enum SqlMethod {
        DELETE, UPDATE, CREATE
    }

    public JdbcTableWriter (Table specTable, DataSource dataSource, String tablePrefix, Connection optionalConnection) {
        this.tablePrefix = tablePrefix;
        this.dataSource = dataSource;
        this.specTable = specTable;
        Connection connection1;
        try {
            connection1 = dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            connection1 = null;
        }
        if (optionalConnection != null) {
            DbUtils.closeQuietly(connection1);
        }
        this.connection = optionalConnection == null ? connection1 : optionalConnection;
    }

    /**
     * Wrapper method to call Jackson to deserialize a JSON string into JsonNode.
     */
    private static JsonNode getJsonNode (String json) throws IOException {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            LOG.error("Bad JSON syntax", e);
            throw e;
        }
    }

    /**
     * Create a new entity in the database from the provided JSON string. Note, any call to create or update must provide
     * a JSON string with the full set of fields matching the definition of the GTFS table in the Table class.
     */
    @Override
    public String create(String json, boolean autoCommit) throws SQLException, IOException {
        return update(null, json, autoCommit);
    }

    /**
     * Update entity for a given ID with the provided JSON string. This update and any potential cascading updates to
     * referencing tables all happens in a single transaction. Note, any call to create or update must provide
     * a JSON string with the full set of fields matching the definition of the GTFS table in the Table class.
     */
    @Override
    public String update(Integer id, String json, boolean autoCommit) throws SQLException, IOException {
        final boolean isCreating = id == null;
        JsonNode jsonNode = getJsonNode(json);
        try {
            if (jsonNode.isArray()) {
                // If an array of objects is passed in as the JSON input, update them all in a single transaction, only
                // committing once all entities have been updated.
                List<String> updatedObjects = new ArrayList<>();
                for (JsonNode node : jsonNode) {
                    JsonNode idNode = node.get("id");
                    Integer nodeId = idNode == null || isCreating ? null : idNode.asInt();
                    String updatedObject = update(nodeId, node.toString(), false);
                    updatedObjects.add(updatedObject);
                }
                if (autoCommit) connection.commit();
                return mapper.writeValueAsString(updatedObjects);
            }
            // Cast JsonNode to ObjectNode to allow mutations (e.g., updating the ID field).
            ObjectNode jsonObject = (ObjectNode) jsonNode;
            // Ensure that the key field is unique and that referencing tables are updated if the value is updated.
            ensureReferentialIntegrity(connection, jsonObject, tablePrefix, specTable, id);
            // Parse the fields/values into a Field -> String map (drops ALL fields not explicitly listed in spec table's
            // fields)
            // Note, this must follow referential integrity check because some tables will modify the jsonObject (e.g.,
            // adding trip ID if it is null).
//            LOG.info("JSON to {} entity: {}", isCreating ? "create" : "update", jsonObject.toString());
            PreparedStatement preparedStatement = createPreparedUpdate(id, isCreating, jsonObject, specTable, connection, false);
            // ID from create/update result
            long newId = handleStatementExecution(preparedStatement, isCreating);
            // At this point, the transaction was successful (but not yet committed). Now we should handle any update
            // logic that applies to child tables. For example, after saving a trip, we need to store its stop times.
            Set<Table> referencingTables = getReferencingTables(specTable);
            // FIXME: hacky hack hack to add shapes table if we're updating a pattern.
            if (specTable.name.equals("patterns")) {
                referencingTables.add(Table.SHAPES);
            }
            // Iterate over referencing (child) tables and update those rows that reference the parent entity with the
            // JSON array for the key that matches the child table's name (e.g., trip.stop_times array will trigger
            // update of stop_times with matching trip_id).
            for (Table referencingTable : referencingTables) {
                Table parentTable = referencingTable.getParentTable();
                if (parentTable != null && parentTable.name.equals(specTable.name) || referencingTable.name.equals("shapes")) {
                    // If a referencing table has the current table as its parent, update child elements.
                    JsonNode childEntities = jsonObject.get(referencingTable.name);
                    if (childEntities == null || childEntities.isNull() || !childEntities.isArray()) {
                        throw new SQLException(String.format("Child entities %s must be an array and not null", referencingTable.name));
                    }
                    int entityId = isCreating ? (int) newId : id;
                    // Cast child entities to array node to iterate over.
                    ArrayNode childEntitiesArray = (ArrayNode)childEntities;
                    updateChildTable(childEntitiesArray, entityId, isCreating, referencingTable, connection);
                }
            }
            // Iterate over table's fields and apply linked values to any tables
            if ("routes".equals(specTable.name)) {
                updateLinkedFields(specTable, jsonObject, "trips", "route_id", "wheelchair_accessible");
            } else if ("patterns".equals(specTable.name)) {
                updateLinkedFields(specTable, jsonObject, "trips", "pattern_id", "direction_id");
            }
            if (autoCommit) {
                // If nothing failed up to this point, it is safe to assume there were no problems updating/creating the
                // main entity and any of its children, so we commit the transaction.
                LOG.info("Committing transaction.");
                connection.commit();
            }
            // Add new ID to JSON object.
            jsonObject.put("id", newId);
            // FIXME: Should this return the entity freshly queried from the database rather than just updating the ID?
            return jsonObject.toString();
        } catch (Exception e) {
            LOG.error("Error {} {} entity", isCreating ? "creating" : "updating", specTable.name);
            e.printStackTrace();
            throw e;
        } finally {
            if (autoCommit) {
                // Always rollback and close in finally in case of early returns or exceptions.
                connection.rollback();
                connection.close();
            }
        }
    }

    /**
     * Updates linked fields with values from entity being updated. This is used to update identical fields in related
     * tables (for now just fields in trips and stop_times) where the reference table's value should take precedence over
     * the related table (e.g., pattern_stop#timepoint should update all of its related stop_times).
     */
    private void updateLinkedFields(Table referenceTable, ObjectNode jsonObject, String tableName, String keyField, String ...fieldNames) throws SQLException {
        // Collect fields, the JSON values for these fields, and the strings to add to the prepared statement into Lists.
        List<Field> fields = new ArrayList<>();
        List<JsonNode> values = new ArrayList<>();
        List<String> fieldStrings = new ArrayList<>();
        for (String field : fieldNames) {
            fields.add(referenceTable.getFieldForName(field));
            values.add(jsonObject.get(field));
            fieldStrings.add(String.format("%s = ?", field));
        }
        String setFields = String.join(", ", fieldStrings);
        // If updating stop_times, use a more complex query that joins trips to stop_times in order to match on pattern_id
        boolean updatingStopTimes = "stop_times".equals(tableName);
        Field orderField = updatingStopTimes ? referenceTable.getFieldForName(referenceTable.getOrderFieldName()) : null;
        String sql = updatingStopTimes
            ? String.format("update %s.stop_times st set %s from %s.trips t " +
                        "where st.trip_id = t.trip_id AND t.%s = ? AND st.%s = ?",
                tablePrefix, setFields, tablePrefix, keyField, orderField.name)
            : String.format("update %s.%s set %s where %s = ?", tablePrefix, tableName, setFields, keyField);
        // Prepare the statement and set statement parameters
        PreparedStatement statement = connection.prepareStatement(sql);
        int oneBasedIndex = 1;
        // Iterate over list of fields that need to be updated and set params.
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            String newValue = values.get(i).isNull() ? null : values.get(i).asText();
            if (newValue == null) field.setNull(statement, oneBasedIndex++);
            else field.setParameter(statement, oneBasedIndex++, newValue);
        }
        // Set "where clause" with value for key field (e.g., set values where pattern_id = '3')
        statement.setString(oneBasedIndex++, jsonObject.get(keyField).asText());
        if (updatingStopTimes) {
            // If updating stop times set the order field parameter (stop_sequence)
            String orderValue = jsonObject.get(orderField.name).asText();
            orderField.setParameter(statement, oneBasedIndex++, orderValue);
        }
        // Log query, execute statement, and log result.
        LOG.info(statement.toString());
        int entitiesUpdated = statement.executeUpdate();
        LOG.info("{} {} linked fields updated", entitiesUpdated, tableName);
    }

    /**
     * Creates a prepared statement for an entity create or update operation. If not performing a batch operation, the
     * method will set parameters for the prepared statement with values found in the provided JSON ObjectNode. The Table
     * object here is provided as a positional argument (rather than provided via the JdbcTableWriter instance field)
     * because this method is used to update both the specTable for the primary entity and any relevant child entities.
     */
    private PreparedStatement createPreparedUpdate(Integer id, boolean isCreating, ObjectNode jsonObject, Table table, Connection connection, boolean batch) throws SQLException {
        String statementString;
        if (isCreating) {
            statementString = table.generateInsertSql(tablePrefix, true);
        } else {
            statementString = table.generateUpdateSql(tablePrefix, id);
        }
        // Set the RETURN_GENERATED_KEYS flag on the PreparedStatement because it may be creating new rows, in which
        // case we need to know the auto-generated IDs of those new rows.
        PreparedStatement preparedStatement = connection.prepareStatement(
                statementString,
                Statement.RETURN_GENERATED_KEYS);
        if (!batch) {
            setStatementParameters(jsonObject, table, preparedStatement, connection);
        }
        return preparedStatement;
    }

    /**
     * Given a prepared statement (for update or create), set the parameters of the statement based on string values
     * taken from JSON. Note, string values are used here in order to take advantage of setParameter method on
     * individual fields, which handles parsing string and non-string values into the appropriate SQL field types.
     */
    private void setStatementParameters(ObjectNode jsonObject, Table table, PreparedStatement preparedStatement, Connection connection) throws SQLException {
        // JDBC SQL statements use a one-based index for setting fields/parameters
        List<String> missingFieldNames = new ArrayList<>();
        int index = 1;
        for (Field field : table.editorFields()) {
            if (!jsonObject.has(field.name)) {
                // If there is a field missing from the JSON string and it is required to write to an editor table,
                // throw an exception (handled after the fields iteration. In an effort to keep the database integrity
                // intact, every update/create operation should have all fields defined by the spec table.
                // FIXME: What if someone wants to make updates to non-editor feeds? In this case, the table may not
                // have all of the required fields, yet this would prohibit such an update. Further, an update on such
                // a table that DID have all of the spec table fields would fail because they might be missing from
                // the actual database table.
                missingFieldNames.add(field.name);
                continue;
            }
            JsonNode value = jsonObject.get(field.name);
//            LOG.info("{}={}", field.name, value);
            try {
                if (value == null || value.isNull()) {
                    if (field.isRequired()) {
                        missingFieldNames.add(field.name);
                        continue;
                    }
                    // Handle setting null value on statement
                    field.setNull(preparedStatement, index);
                } else {
                    List<String> values = new ArrayList<>();
                    if (value.isArray()) {
                        for (JsonNode node : value) {
                            values.add(node.asText());
                        }
                        field.setParameter(preparedStatement, index, String.join(",", values));
                    } else {
                        field.setParameter(preparedStatement, index, value.asText());
                    }
                }
            } catch (StorageException e) {
                LOG.warn("Could not set field {} to value {}. Attempting to parse integer seconds.", field.name, value);
                if (field.name.contains("_time")) {
                    // FIXME: This is a hack to get arrival and departure time into the right format. Because the UI
                    // currently returns them as seconds since midnight rather than the Field-defined format HH:MM:SS.
                    try {
                        if (value == null ||value.isNull()) {
                            if (field.isRequired()) {
                                missingFieldNames.add(field.name);
                                continue;
                            }
                            field.setNull(preparedStatement, index);
                        } else {
                            // Try to parse integer seconds value
                            preparedStatement.setInt(index, Integer.parseInt(value.asText()));
                            LOG.info("Parsing value {} for field {} successful!", value, field.name);
                        }
                    } catch (NumberFormatException ex) {
                        // Attempt to set arrival or departure time via integer seconds failed. Rollback.
                        connection.rollback();
                        LOG.error("Bad column: {}={}", field.name, value);
                        ex.printStackTrace();
                        throw ex;
                    }
                } else {
                    // Rollback transaction and throw exception
                    connection.rollback();
                    throw e;
                }
            }
            index += 1;
        }
        if (missingFieldNames.size() > 0) {
//            String joinedFieldNames = missingFieldNames.stream().collect(Collectors.joining(", "));
            throw new SQLException(String.format("The following field(s) are missing from JSON %s object: %s", table.name, missingFieldNames.toString()));
        }
    }

    /**
     * This updates those tables that depend on the table currently being updated. For example, if updating/creating a
     * pattern, this method handles deleting any pattern stops and shape points. For trips, this would handle updating
     * the trips' stop times.
     *
     * This method should only be used on tables that have a single foreign key reference to another table, i.e., they
     * have a hierarchical relationship.
     * FIXME develop a better way to update tables with foreign keys to the table being updated.
     */
    private void updateChildTable(ArrayNode entityList, Integer id, boolean isCreatingNewEntity, Table subTable, Connection connection) throws SQLException {
        // Get parent table's key field
        Field keyField;
        String keyValue;
        // Primary key fields are always referenced by foreign key fields with the same name.
        keyField = specTable.getFieldForName(subTable.getKeyFieldName());
        // Get parent entity's key value
        keyValue = getValueForId(id, keyField.name, tablePrefix, specTable, connection);
        String childTableName = String.join(".", tablePrefix, subTable.name);
        // FIXME: add check for pattern stop consistency.
        // FIXME: re-order stop times if pattern stop order changes.
        // FIXME: allow shapes to be updated on pattern geometry change.
        if (!isCreatingNewEntity) {
            // Delete existing sub-entities for given entity ID if the parent entity is not being newly created.
            String deleteSql = getUpdateReferencesSql(SqlMethod.DELETE, childTableName, keyField, keyValue, null);
            LOG.info(deleteSql);
            Statement statement = connection.createStatement();
            // FIXME: Use copy on update for a pattern's shape instead of deleting the previous shape and replacing it.
            // This would better account for GTFS data loaded from a file where multiple patterns reference a single
            // shape.
            int result = statement.executeUpdate(deleteSql);
            LOG.info("Deleted {}", result);
            // FIXME: are there cases when an update should not return zero?
//            if (result == 0) throw new SQLException("No stop times found for trip ID");
        }
        int entityCount = 0;
        PreparedStatement insertStatement = null;
        // Iterate over the entities found in the array and add to batch for inserting into table.
        for (JsonNode entityNode : entityList) {
            // Cast entity node to ObjectNode to allow mutations (JsonNode is immutable).
            ObjectNode entity = (ObjectNode)entityNode;
            if (entity.get(keyField.name) == null || entity.get(keyField.name).isNull()) {
                entity.put(keyField.name, keyValue);
            }
            // Insert new sub-entity.
            if (entityCount == 0) {
                insertStatement = createPreparedUpdate(id, true, entity, subTable, connection, true);
            }
            // Update linked stop times fields for updated pattern stop (e.g., timepoint, pickup/drop off type).
            if ("pattern_stops".equals(subTable.name)) {
                updateLinkedFields(subTable, entity, "stop_times", "pattern_id", "timepoint", "drop_off_type", "pickup_type", "shape_dist_traveled");
            }
            setStatementParameters(entity, subTable, insertStatement, connection);
            if (entityCount == 0) LOG.info(insertStatement.toString());
            insertStatement.addBatch();
            // Prefix increment count and check whether to execute batched update.
            if (++entityCount % INSERT_BATCH_SIZE == 0) {
                LOG.info("Executing batch insert ({}/{}) for {}", entityCount, entityList.size(), childTableName);
                int[] newIds = insertStatement.executeBatch();
                LOG.info("Updated {}", newIds.length);
            }
        }
        // execute any remaining prepared statement calls
        LOG.info("Executing batch insert ({}/{}) for {}", entityCount, entityList.size(), childTableName);
        if (insertStatement != null) {
            // If insert statement is null, an empty array was passed for the child table, so the child elements have
            // been wiped.
            int[] newIds = insertStatement.executeBatch();
            LOG.info("Updated {} {} child entities", newIds.length, subTable.name);
        } else {
            LOG.info("No inserts to execute. Empty array found in JSON for child table {}", childTableName);
        }
    }

    /**
     * For a given condition (fieldName = 'value'), delete all entities that match the condition. Because this uses the
     * primary delete method, it also will delete any "child" entities that reference any entities matching the original
     * query.
     */
    @Override
    public int deleteWhere(String fieldName, String value, boolean autoCommit) throws SQLException {
        try {
            String tableName = String.join(".", tablePrefix, specTable.name);
            // Get the IDs for entities matching the where condition
            TIntSet idsToDelete = getIdsForCondition(tableName, fieldName, value, connection);
            TIntIterator iterator = idsToDelete.iterator();
            TIntList results = new TIntArrayList();
            while (iterator.hasNext()) {
                // For all entity IDs that match query, delete from referencing tables.
                int id = iterator.next();
                // FIXME: Should this be a where in clause instead of iterating over IDs?
                // Delete each entity and its referencing (child) entities
                int result = delete(id, false);
                if (result != 1) {
                    throw new SQLException("Could not delete entity with ID " + id);
                }
                results.add(result);
            }
            if (autoCommit) connection.commit();
            LOG.info("Deleted {} {} entities", results.size(), specTable.name);
            return results.size();
        } catch (Exception e) {
            connection.rollback();
            LOG.error("Could not delete {} entity where {}={}", specTable.name, fieldName, value);
            e.printStackTrace();
            throw e;
        } finally {
            if (autoCommit) {
                // Always rollback and close if auto-committing.
                connection.rollback();
                connection.close();
            }
        }
    }

    /**
     * Deletes an entity for the specified ID.
     */
    @Override
    public int delete(Integer id, boolean autoCommit) throws SQLException {
        try {
            // Handle "cascading" delete or constraints on deleting entities that other entities depend on
            // (e.g., keep a calendar from being deleted if trips reference it).
            // FIXME: actually add "cascading"? Currently, it just deletes one level down.
            deleteFromReferencingTables(tablePrefix, specTable, connection, id);
            PreparedStatement statement = connection.prepareStatement(specTable.generateDeleteSql(tablePrefix));
            statement.setInt(1, id);
            LOG.info(statement.toString());
            // Execute query
            int result = statement.executeUpdate();
            if (result == 0) {
                LOG.error("Could not delete {} entity with id: {}", specTable.name, id);
                throw new SQLException("Could not delete entity");
            }
            if (autoCommit) connection.commit();
            // FIXME: change return message based on result value
            return result;
        } catch (Exception e) {
            LOG.error("Could not delete {} entity with id: {}", specTable.name, id);
            e.printStackTrace();
            throw e;
        } finally {
            if (autoCommit) {
                // Always rollback and close if auto-committing.
                connection.rollback();
                connection.close();
            }
        }
    }

    @Override
    public void commit() throws SQLException {
        // FIXME: should this take a connection and commit it?
        connection.commit();
        connection.close();
    }

    /**
     * Delete entities from any referencing tables (if required). This method is defined for convenience and clarity, but
     * essentially just runs updateReferencingTables with a null value for newKeyValue param.
     */
    private static void deleteFromReferencingTables(String namespace, Table table, Connection connection, int id) throws SQLException {
        updateReferencingTables(namespace, table, connection, id, null);
    }

    /**
     * Handle executing a prepared statement and return the ID for the newly-generated or updated entity.
     */
    private static long handleStatementExecution(PreparedStatement statement, boolean isCreating) throws SQLException {
        // Log the SQL for the prepared statement
        LOG.info(statement.toString());
        int affectedRows = statement.executeUpdate();
        // Determine operation-specific action for any error messages
        String messageAction = isCreating ? "Creating" : "Updating";
        if (affectedRows == 0) {
            // No update occurred.
            // TODO: add some clarity on cause (e.g., where clause found no entity with provided ID)?
            throw new SQLException(messageAction + " entity failed, no rows affected.");
        }
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                // Get the auto-generated ID from the update execution
                long newId = generatedKeys.getLong(1);
                return newId;
            } else {
                throw new SQLException(messageAction + " entity failed, no ID obtained.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Checks for modification of GTFS key field (e.g., stop_id, route_id) in supplied JSON object and ensures
     * both uniqueness and that referencing tables are appropriately updated.
     *
     * FIXME: add more detail/precise language on what this method actually does
     */
    private static void ensureReferentialIntegrity(Connection connection, ObjectNode jsonObject, String namespace, Table table, Integer id) throws SQLException {
        final boolean isCreating = id == null;
        String keyField = table.getKeyFieldName();
        String tableName = String.join(".", namespace, table.name);
        if (jsonObject.get(keyField) == null || jsonObject.get(keyField).isNull()) {
            // FIXME: generate key field automatically for certain entities (e.g., trip ID). Maybe this should be
            // generated for all entities if null?
            if ("trip_id".equals(keyField)) {
                jsonObject.put(keyField, UUID.randomUUID().toString());
            } else if ("agency_id".equals(keyField)) {
                LOG.warn("agency_id field for agency id={} is null.", id);
                int rowSize = getRowCount(tableName, connection);
                if (rowSize > 1 || (isCreating && rowSize > 0)) {
                    throw new SQLException("agency_id must not be null if more than one agency exists.");
                }
            } else {
                throw new SQLException(String.format("Key field %s must not be null", keyField));
            }
        }
        String keyValue = jsonObject.get(keyField).asText();
        // If updating key field, check that there is no ID conflict on value (e.g., stop_id or route_id)
        TIntSet uniqueIds = getIdsForCondition(tableName, keyField, keyValue, connection);
        int size = uniqueIds.size();
        if (size == 0 || (size == 1 && id != null && uniqueIds.contains(id))) {
            // OK.
            if (size == 0 && !isCreating) {
                // FIXME: Need to update referencing tables because entity has changed ID.
                // Entity key value is being changed to an entirely new one.  If there are entities that
                // reference this value, we need to update them.
                updateReferencingTables(namespace, table, connection, id, keyValue);
            }
        } else {
            // Conflict. The different conflict conditions are outlined below.
            if (size == 1) {
                // There was one match found.
                if (isCreating) {
                    // Under no circumstance should a new entity have a conflict with existing key field.
                    throw new SQLException("New entity's key field must not match existing value.");
                }
                if (!uniqueIds.contains(id)) {
                    // There are two circumstances we could encounter here.
                    // 1. The key value for this entity has been updated to match some other entity's key value (conflict).
                    // 2. The int ID provided in the request parameter does not match any rows in the table.
                    throw new SQLException("Key field must be unique and request parameter ID must exist.");
                }
            } else if (size > 1) {
                // FIXME: Handle edge case where original data set contains duplicate values for key field and this is an
                // attempt to rectify bad data.
                LOG.warn("{} entity shares the same key field ({}={})! This is Bad!!", size, keyField, keyValue);
                throw new SQLException("More than one entity must not share the same id field");
            }
        }
    }

    /**
     * Get number of rows for a table. This is currently just used to check the number of entities for the agency table.
     */
    private static int getRowCount(String tableName, Connection connection) throws SQLException {
        String rowCountSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        LOG.info(rowCountSql);
        // Create statement for counting rows selected
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(rowCountSql);
        if (resultSet.next()) return resultSet.getInt(1);
        else return 0;
    }

    /**
     * For some condition (where field = string value), return the set of unique int IDs for the records that match.
     */
    private static TIntSet getIdsForCondition(String tableName, String keyField, String keyValue, Connection connection) throws SQLException {
        String idCheckSql = String.format("select id from %s where %s = '%s'", tableName, keyField, keyValue);
        LOG.info(idCheckSql);
        // Create statement for counting rows selected
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(idCheckSql);
        // Keep track of number of records found with key field
        TIntSet uniqueIds = new TIntHashSet();
        while (resultSet.next()) {
            int uniqueId = resultSet.getInt(1);
            uniqueIds.add(uniqueId);
            LOG.info("entity id: {}, where {}: {}", uniqueId, keyField, keyValue);
        }
        return uniqueIds;
    }

    /**
     * Finds the set of tables that reference the parent entity being updated.
     */
    private static Set<Table> getReferencingTables(Table table) {
        String keyField = table.getKeyFieldName();
        Set<Table> referencingTables = new HashSet<>();
        for (Table gtfsTable : Table.tablesInOrder) {
            // IMPORTANT: Skip the table for the entity we're modifying or if loop table does not have field.
            if (table.name.equals(gtfsTable.name)) continue;
//            || !gtfsTable.hasField(keyField)
            for (Field field : gtfsTable.fields) {
                if (field.isForeignReference() && field.referenceTable.name.equals(table.name)) {
                    // If any of the table's fields are foreign references to the specified table, add to the return set.
                    referencingTables.add(gtfsTable);
                }
            }
//            Field tableField = gtfsTable.getFieldForName(keyField);
//            // If field is not a foreign reference, continue. (This should probably never be the case because a field
//            // that shares the key field's name ought to refer to the key field.
//            if (!tableField.isForeignReference()) continue;

        }
        return referencingTables;
    }

    /**
     * For a given integer ID, return the value for the specified field name for that entity.
     */
    private static String getValueForId(int id, String fieldName, String namespace, Table table, Connection connection) throws SQLException {
        String tableName = String.join(".", namespace, table.name);
        String selectIdSql = String.format("select %s from %s where id = %d", fieldName, tableName, id);
        LOG.info(selectIdSql);
        Statement selectIdStatement = connection.createStatement();
        ResultSet selectResults = selectIdStatement.executeQuery(selectIdSql);
        String keyValue = null;
        while (selectResults.next()) {
            keyValue = selectResults.getString(1);
        }
        return keyValue;
    }

    /**
     * Updates any foreign references that exist should a GTFS key field (e.g., stop_id or route_id) be updated via an
     * HTTP request for a given integer ID. First, all GTFS tables are filtered to find referencing tables. Then records
     * in these tables that match the old key value are modified to match the new key value.
     *
     * The function determines whether the method is update or delete depending on the presence of the newKeyValue
     * parameter (if null, the method is DELETE). Custom logic/hooks could be added here to check if there are entities
     * referencing the entity being updated.
     *
     * FIXME: add custom logic/hooks. Right now entity table checks are hard-coded in (e.g., if Agency, skip all. OR if
     * Calendar, rollback transaction if there are referencing trips).
     *
     * FIXME: Do we need to clarify the impact of the direction of the relationship (e.g., if we delete a trip, that should
     * not necessarily delete a shape that is shared by multiple trips)? I think not because we are skipping foreign refs
     * found in the table for the entity being updated/deleted. [Leaving this comment in place for now though.]
     */
    private static void updateReferencingTables(String namespace, Table table, Connection connection, int id, String newKeyValue) throws SQLException {
        Field keyField = table.getFieldForName(table.getKeyFieldName());
        Class<? extends Entity> entityClass = table.getEntityClass();
        // Determine method (update vs. delete) depending on presence of newKeyValue field.
        SqlMethod sqlMethod = newKeyValue != null ? SqlMethod.UPDATE : SqlMethod.DELETE;
        Set<Table> referencingTables = getReferencingTables(table);
        // If there are no referencing tables, there is no need to update any values (e.g., .
        if (referencingTables.size() == 0) return;
        String keyValue = getValueForId(id, keyField.name, namespace, table, connection);
        if (keyValue == null) {
            // FIXME: should we still check referencing tables for null value?
            LOG.warn("Entity {} to {} has null value for {}. Skipping references check.", id, sqlMethod, keyField);
            return;
        }
        for (Table referencingTable : referencingTables) {
            // Update/delete foreign references that have match the key value.
            String refTableName = String.join(".", namespace, referencingTable.name);
            for (Field field : referencingTable.editorFields()) {
                if (field.isForeignReference() && field.referenceTable.name.equals(table.name)) {
                    // FIXME: Are there other references that are not being captured???
                    // Cascade delete stop times and frequencies for trips. This must happen before trips are deleted
                    // below. Otherwise, there are no trips with which to join.
                    if ("trips".equals(referencingTable.name)) {
                        String stopTimesTable = String.join(".", namespace, "stop_times");
                        String frequenciesTable = String.join(".", namespace, "frequencies");
                        String tripsTable = String.join(".", namespace, "trips");
                        // Delete stop times and frequencies for trips for pattern
                        String deleteStopTimes = String.format(
                                "delete from %s using %s where %s.trip_id = %s.trip_id and %s.pattern_id = '%s'",
                                stopTimesTable, tripsTable, stopTimesTable, tripsTable, tripsTable, keyValue);
                        LOG.info(deleteStopTimes);
                        PreparedStatement deleteStopTimesStatement = connection.prepareStatement(deleteStopTimes);
                        int deletedStopTimes = deleteStopTimesStatement.executeUpdate();
                        LOG.info("Deleted {} stop times for pattern {}", deletedStopTimes, keyValue);
                        String deleteFrequencies = String.format(
                                "delete from %s using %s where %s.trip_id = %s.trip_id and %s.pattern_id = '%s'",
                                frequenciesTable, tripsTable, frequenciesTable, tripsTable, tripsTable, keyValue);
                        LOG.info(deleteFrequencies);
                        PreparedStatement deleteFrequenciesStatement = connection.prepareStatement(deleteFrequencies);
                        int deletedFrequencies = deleteFrequenciesStatement.executeUpdate();
                        LOG.info("Deleted {} frequencies for pattern {}", deletedFrequencies, keyValue);
                    }
                    // Get unique IDs before delete (for logging/message purposes).
                    // TIntSet uniqueIds = getIdsForCondition(refTableName, keyField, keyValue, connection);
                    String updateRefSql = getUpdateReferencesSql(sqlMethod, refTableName, field, keyValue, newKeyValue);
                    LOG.info(updateRefSql);
                    Statement updateStatement = connection.createStatement();
                    int result = updateStatement.executeUpdate(updateRefSql);
                    if (result > 0) {
                        // FIXME: is this where a delete hook should go? (E.g., CalendarController subclass would override
                        // deleteEntityHook).
                        // deleteEntityHook();
                        if (sqlMethod.equals(SqlMethod.DELETE)) {
                            // Check for restrictions on delete.
                            if (table.isCascadeDeleteRestricted()) {
                                // The entity must not have any referencing entities in order to delete it.
                                connection.rollback();
                                //                        List<String> idStrings = new ArrayList<>();
                                //                        uniqueIds.forEach(uniqueId -> idStrings.add(String.valueOf(uniqueId)));
                                //                        String message = String.format("Cannot delete %s %s=%s. %d %s reference this %s (%s).", entityClass.getSimpleName(), keyField, keyValue, result, referencingTable.name, entityClass.getSimpleName(), String.join(",", idStrings));
                                String message = String.format("Cannot delete %s %s=%s. %d %s reference this %s.", entityClass.getSimpleName(), keyField.name, keyValue, result, referencingTable.name, entityClass.getSimpleName());
                                LOG.warn(message);
                                throw new SQLException(message);
                            }
                        }
                        LOG.info("{} reference(s) in {} {}D!", result, refTableName, sqlMethod);
                    } else {
                        LOG.info("No references in {} found!", refTableName);
                    }
                }
            }
        }
    }

    /**
     * Constructs SQL string based on method provided.
     */
    private static String getUpdateReferencesSql(SqlMethod sqlMethod, String refTableName, Field keyField, String keyValue, String newKeyValue) throws SQLException {
        boolean isArrayField = keyField.getSqlType().equals(JDBCType.ARRAY);
        switch (sqlMethod) {
            case DELETE:
                if (isArrayField) {
                    return String.format("delete from %s where %s @> ARRAY['%s']::text[]", refTableName, keyField.name, keyValue);
                } else {
                    return String.format("delete from %s where %s = '%s'", refTableName, keyField.name, keyValue);
                }
            case UPDATE:
                if (isArrayField) {
                    // If the field to be updated is an array field (of which there are only text[] types in the db),
                    // replace the old value with the new value using array contains clause.
                    // FIXME This is probably horribly postgres specific.
                    return String.format("update %s set %s = array_replace(%s, '%s', '%s') where %s @> ARRAY['%s']::text[]", refTableName, keyField.name, keyField.name, keyValue, newKeyValue, keyField.name, keyValue);
                } else {
                    return String.format("update %s set %s = '%s' where %s = '%s'", refTableName, keyField.name, newKeyValue, keyField.name, keyValue);
                }
//            case CREATE:
//                return String.format("insert into %s ");
            default:
                throw new SQLException("SQL Method must be DELETE or UPDATE.");
        }
    }
}