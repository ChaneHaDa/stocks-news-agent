package com.newsagent.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL pgvector extension and index initialization
 * Only runs when using PostgreSQL database
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class PgVectorInitializer {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Value("${app.database.type:auto}")
    private String databaseType;
    
    @Bean
    public ApplicationRunner initializePgVector() {
        return args -> {
            if (!isPostgreSQL()) {
                log.info("Skipping pgvector initialization - not using PostgreSQL (detected: {})", detectDatabaseType());
                return;
            }
            
            try {
                // Check if pgvector extension is available
                if (isPgVectorAvailable()) {
                    log.info("pgvector extension is available, creating vector indexes");
                    createVectorIndexes();
                } else {
                    log.warn("pgvector extension not available, using fallback text-based similarity");
                }
            } catch (Exception e) {
                log.error("Failed to initialize pgvector - continuing with text-based fallback", e);
            }
        };
    }
    
    private boolean isPgVectorAvailable() {
        try {
            // Try to enable pgvector extension
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            
            // Check if vector type is available
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_type WHERE typname = 'vector'", 
                Integer.class
            );
            
            boolean available = count != null && count > 0;
            log.info("pgvector extension check: {}", available ? "available" : "not available");
            return available;
            
        } catch (DataAccessException e) {
            log.debug("pgvector extension not available: {}", e.getMessage());
            return false;
        }
    }
    
    private void createVectorIndexes() {
        try {
            // First, alter the table to use proper VECTOR type if needed
            log.info("Attempting to alter news_embedding_v2 table for pgvector support");
            
            // Check if vector_pg column is already VECTOR type
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE news_embedding_v2 " +
                    "ALTER COLUMN vector_pg TYPE VECTOR(768) USING vector_pg::vector"
                );
                log.info("Successfully converted vector_pg column to VECTOR(768) type");
            } catch (DataAccessException e) {
                log.debug("Could not convert vector_pg column type: {}", e.getMessage());
            }
            
            // Create HNSW indexes for fast similarity search
            try {
                // Drop existing indexes if they exist
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_news_embedding_v2_vector_cosine");
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_news_embedding_v2_vector_l2");
                
                // Create new HNSW indexes
                jdbcTemplate.execute(
                    "CREATE INDEX idx_news_embedding_v2_vector_cosine " +
                    "ON news_embedding_v2 USING hnsw (vector_pg vector_cosine_ops)"
                );
                
                jdbcTemplate.execute(
                    "CREATE INDEX idx_news_embedding_v2_vector_l2 " +
                    "ON news_embedding_v2 USING hnsw (vector_pg vector_l2_ops)"
                );
                
                log.info("Successfully created pgvector HNSW indexes for similarity search");
                
            } catch (DataAccessException e) {
                log.warn("Could not create pgvector indexes: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Failed to create vector indexes", e);
        }
    }
    
    private boolean isPostgreSQL() {
        if ("postgresql".equalsIgnoreCase(databaseType)) {
            return true;
        }
        
        if ("auto".equalsIgnoreCase(databaseType)) {
            return "postgresql".equalsIgnoreCase(detectDatabaseType());
        }
        
        return false;
    }
    
    private String detectDatabaseType() {
        try {
            return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
                String dbProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
                if (dbProduct.contains("postgresql")) {
                    return "postgresql";
                } else if (dbProduct.contains("h2")) {
                    return "h2";
                } else {
                    return dbProduct;
                }
            });
        } catch (Exception e) {
            log.debug("Could not detect database type: {}", e.getMessage());
            return "unknown";
        }
    }
}