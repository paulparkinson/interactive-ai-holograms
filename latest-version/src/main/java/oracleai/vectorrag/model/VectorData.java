// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

package oracleai.vectorrag.model;

import oracle.sql.json.OracleJsonObject;

public class VectorData {
    private String id;
    private double[]  embeddings; 
    private String text ;
    private OracleJsonObject metadata;
    
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public OracleJsonObject getMetadata() {
        return metadata;
    }

    public void setMetadata(OracleJsonObject metadata) {
        this.metadata = metadata;
    }

    // Constructor
    public VectorData(String id, double[] embeddings, String text, OracleJsonObject metadata) {
        this.id = id;
        this.embeddings = embeddings;
        this.metadata = metadata;
        this.text = text;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double[] getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(double[] embeddings) {
        this.embeddings = embeddings;
    }
}
