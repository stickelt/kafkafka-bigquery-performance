package com.example.kafkabqperformance.service;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.storage.v1.TableFieldSchema;
import com.google.cloud.bigquery.storage.v1.TableSchema;

/**
 * Utility class to convert between BigQuery Schema and Storage API TableSchema
 */
public class SchemaTranslator {
    
    /**
     * Converts a BigQuery Schema to TableSchema for BigQuery Storage Write API
     * 
     * @param bqSchema the BigQuery Schema to convert
     * @return converted TableSchema for Storage API
     */
    public static TableSchema toTableSchema(Schema bqSchema) {
        TableSchema.Builder builder = TableSchema.newBuilder();
        
        bqSchema.getFields().forEach(field -> {
            TableFieldSchema.Builder fieldBuilder = TableFieldSchema.newBuilder()
                .setName(field.getName())
                .setType(convertType(field.getType().name()));
            
            if (field.getMode() != null) {
                fieldBuilder.setMode(convertMode(field.getMode().name()));
            } else {
                // Always set a default mode if null to avoid NPE
                fieldBuilder.setMode(TableFieldSchema.Mode.NULLABLE);
            }
            
            if (field.getSubFields() != null && !field.getSubFields().isEmpty()) {
                for (Field subField : field.getSubFields()) {
                    TableFieldSchema.Builder subFieldBuilder = TableFieldSchema.newBuilder()
                        .setName(subField.getName())
                        .setType(convertType(subField.getType().name()));
                    
                    // Always set a mode for subfields to avoid NPE
                    if (subField.getMode() != null) {
                        subFieldBuilder.setMode(convertMode(subField.getMode().name()));
                    } else {
                        subFieldBuilder.setMode(TableFieldSchema.Mode.NULLABLE);
                    }
                    
                    // Handle recursive nested fields if present
                    if (subField.getSubFields() != null && !subField.getSubFields().isEmpty()) {
                        for (Field nestedSubField : subField.getSubFields()) {
                            TableFieldSchema.Builder nestedFieldBuilder = TableFieldSchema.newBuilder()
                                .setName(nestedSubField.getName())
                                .setType(convertType(nestedSubField.getType().name()));
                                
                            if (nestedSubField.getMode() != null) {
                                nestedFieldBuilder.setMode(convertMode(nestedSubField.getMode().name()));
                            } else {
                                nestedFieldBuilder.setMode(TableFieldSchema.Mode.NULLABLE);
                            }
                            
                            subFieldBuilder.addFields(nestedFieldBuilder.build());
                        }
                    }
                    
                    fieldBuilder.addFields(subFieldBuilder.build());
                }
            }
            
            builder.addFields(fieldBuilder.build());
        });
        
        return builder.build();
    }
    
    /**
     * Convert BigQuery type names to Storage API type enum
     */
    private static TableFieldSchema.Type convertType(String typeName) {
        switch (typeName) {
            case "STRING": return TableFieldSchema.Type.STRING;
            case "INT64": case "INTEGER": return TableFieldSchema.Type.INT64;
            case "FLOAT64": case "FLOAT": return TableFieldSchema.Type.DOUBLE;
            case "BOOL": case "BOOLEAN": return TableFieldSchema.Type.BOOL;
            case "TIMESTAMP": return TableFieldSchema.Type.TIMESTAMP;
            case "DATETIME": return TableFieldSchema.Type.DATETIME;
            case "DATE": return TableFieldSchema.Type.DATE;
            case "TIME": return TableFieldSchema.Type.TIME;
            case "NUMERIC": return TableFieldSchema.Type.NUMERIC;
            case "RECORD": case "STRUCT": return TableFieldSchema.Type.STRUCT;
            default: return TableFieldSchema.Type.STRING; // Default
        }
    }
    
    /**
     * Convert BigQuery mode names to Storage API mode enum
     */
    private static TableFieldSchema.Mode convertMode(String modeName) {
        switch (modeName) {
            case "REQUIRED": return TableFieldSchema.Mode.REQUIRED;
            case "REPEATED": return TableFieldSchema.Mode.REPEATED;
            case "NULLABLE": default: return TableFieldSchema.Mode.NULLABLE;
        }
    }
} 