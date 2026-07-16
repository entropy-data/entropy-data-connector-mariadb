package entropydata.mariadb;

import entropydata.sdk.EntropyDataAssetsProvider;
import entropydata.sdk.EntropyDataStateRepository;
import entropydata.sdk.client.model.Asset;
import entropydata.sdk.client.model.AssetColumnsInner;
import entropydata.sdk.client.model.AssetInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to extract assets (schemas, tables, views) from a MariaDB database
 */
public class MariaDbAssetsSupplier implements EntropyDataAssetsProvider {

    private static final Logger log = LoggerFactory.getLogger(MariaDbAssetsSupplier.class);
    
    private final MariaDbProperties properties;
    private final EntropyDataStateRepository stateRepository;

    public MariaDbAssetsSupplier(MariaDbProperties properties, EntropyDataStateRepository stateRepository) {
        this.properties = properties;
        this.stateRepository = stateRepository;
    }

    /**
     * Extracts all schemas, tables and views from the configured MariaDB database
     * and provides them to the callback.
     */
    @Override
    public void fetchAssets(AssetCallback callback) {
        // Get the last processed timestamp from the state repository
        Long lastUpdatedAt = getLastUpdatedAt();
        Long currentTimestamp = System.currentTimeMillis();

        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", 
                properties.connection().host(), 
                properties.connection().port(), 
                properties.connection().database());
        
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, 
                properties.connection().username(), 
                properties.connection().password())) {
            
            // Get database metadata
            DatabaseMetaData metaData = connection.getMetaData();
            
            log.info("Synchronizing MariaDB assets from {}", properties.connection().host());
            
            // Extract schemas (excluding information_schema)
            extractSchema(metaData, callback);
            
            // Get tables from the database
            extractTables(metaData, callback);
            
            // Get views from the database
            extractViews(metaData, callback);
            
            // Update the last processed timestamp
            setLastUpdatedAt(currentTimestamp);
            
        } catch (SQLException e) {
            log.error("Error fetching assets from MariaDB", e);
        }
    }
    
    private void extractTables(DatabaseMetaData metaData, AssetCallback callback) throws SQLException {
        try (ResultSet tablesRS = metaData.getTables(
                properties.connection().database(), null, "%", new String[]{"TABLE"})) {
            
            while (tablesRS.next()) {
                String tableName = tablesRS.getString("TABLE_NAME");
                String tableType = tablesRS.getString("TABLE_TYPE");
                String tableRemarks = tablesRS.getString("REMARKS");
                
                log.info("Synchronizing table {}", tableName);
                
                // Create the Asset
                Asset tableAsset = new Asset();
                
                // Create a consistent ID for the asset
                String assetId = properties.connection().database() + ".TABLE." + tableName;
                tableAsset.setId(assetId);
                
                // Set basic asset information
                AssetInfo info = new AssetInfo();
                info.setName(tableName);
                info.setQualifiedName(properties.connection().database() + "." + tableName);
                info.setType("mariadb_table");
                info.setStatus("active");
                info.setDescription(tableRemarks != null ? tableRemarks : "");
                info.setSource("mariadb");
                info.setSourceId(tableName);
                tableAsset.setInfo(info);
                
                // Add columns for this table
                extractColumns(metaData, tableName, tableAsset);
                
                // Add additional metadata
                tableAsset.putPropertiesItem("host", properties.connection().host());
                tableAsset.putPropertiesItem("database", properties.connection().database());
                tableAsset.putPropertiesItem("table_type", tableType);
                tableAsset.putPropertiesItem("updatedAt", String.valueOf(System.currentTimeMillis()));
                
                // Send to callback
                callback.onAssetUpdated(tableAsset);
            }
        }
    }
    
    private void extractViews(DatabaseMetaData metaData, AssetCallback callback) throws SQLException {
        try (ResultSet viewsRS = metaData.getTables(
                properties.connection().database(), null, "%", new String[]{"VIEW"})) {
            
            while (viewsRS.next()) {
                String viewName = viewsRS.getString("TABLE_NAME");
                String viewRemarks = viewsRS.getString("REMARKS");
                
                log.info("Synchronizing view {}", viewName);
                
                // Create the Asset
                Asset viewAsset = new Asset();
                
                // Create a consistent ID for the asset
                String assetId = properties.connection().database() + ".VIEW." + viewName;
                viewAsset.setId(assetId);
                
                // Set basic asset information
                AssetInfo info = new AssetInfo();
                info.setName(viewName);
                info.setQualifiedName(properties.connection().database() + "." + viewName);
                info.setType("mariadb_view");
                info.setStatus("active");
                info.setDescription(viewRemarks != null ? viewRemarks : "");
                info.setSource("mariadb");
                info.setSourceId(viewName);
                viewAsset.setInfo(info);
                
                // Add columns for this view
                extractColumns(metaData, viewName, viewAsset);
                
                // Add additional metadata
                viewAsset.putPropertiesItem("host", properties.connection().host());
                viewAsset.putPropertiesItem("database", properties.connection().database());
                viewAsset.putPropertiesItem("updatedAt", String.valueOf(System.currentTimeMillis()));
                
                // Send to callback
                callback.onAssetUpdated(viewAsset);
            }
        }
    }
    
    private void extractColumns(DatabaseMetaData metaData, String tableName, Asset asset) throws SQLException {
        try (ResultSet columnsRS = metaData.getColumns(
                properties.connection().database(), null, tableName, "%")) {
            
            while (columnsRS.next()) {
                String columnName = columnsRS.getString("COLUMN_NAME");
                String columnType = columnsRS.getString("TYPE_NAME");
                String columnRemarks = columnsRS.getString("REMARKS");
                int columnSize = columnsRS.getInt("COLUMN_SIZE");
                boolean isNullable = columnsRS.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                
                // Create and add the column
                AssetColumnsInner column = new AssetColumnsInner();
                column.setName(columnName);
                column.setType(columnType);
                column.setDescription(columnRemarks != null ? columnRemarks : "");
                asset.addColumnsItem(column);
                
                // Add column properties as nested properties
                asset.putPropertiesItem(columnName + ".nullable", Boolean.valueOf(isNullable));
                asset.putPropertiesItem(columnName + ".size", Integer.valueOf(columnSize));
            }
        }
    }
    
    private void extractSchema(DatabaseMetaData metaData, AssetCallback callback) throws SQLException {
        // MariaDB doesn't reliably return schemas through the JDBC API's getSchemas() method
        // so we'll add the current database as a schema
        String currentSchema = properties.connection().database();
        
        if (currentSchema != null && !currentSchema.equalsIgnoreCase("information_schema")) {
            log.info("Synchronizing schema {}", currentSchema);
            
            // Create the Asset
            Asset schemaAsset = new Asset();
            
            // Create a consistent ID for the asset
            String assetId = "SCHEMA." + currentSchema;
            schemaAsset.setId(assetId);
            
            // Set basic asset information
            AssetInfo info = new AssetInfo();
            info.setName(currentSchema);
            info.setQualifiedName(currentSchema);
            info.setType("mariadb_schema");
            info.setStatus("active");
            info.setDescription("Database schema: " + currentSchema);
            info.setSource("mariadb");
            info.setSourceId(currentSchema);
            schemaAsset.setInfo(info);
            
            // Add additional metadata
            schemaAsset.putPropertiesItem("host", properties.connection().host());
            schemaAsset.putPropertiesItem("database", properties.connection().database());
            schemaAsset.putPropertiesItem("updatedAt", String.valueOf(System.currentTimeMillis()));
            
            // Send to callback
            callback.onAssetUpdated(schemaAsset);
        }
    }
    
    private Long getLastUpdatedAt() {
        Map<String, Object> state = stateRepository.getState();
        return (Long) state.getOrDefault("lastUpdatedAt", 0L);
    }

    private void setLastUpdatedAt(Long timestamp) {
        Map<String, Object> state = Map.of("lastUpdatedAt", timestamp);
        stateRepository.saveState(state);
    }
}