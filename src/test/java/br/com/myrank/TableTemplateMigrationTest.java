package br.com.myrank;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TableTemplateMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void v9PreservesLegacyItemsSubcategoriesSocialReferencesAndRankingOrder() throws Exception {
        String schema = "template_migration_test_" + Long.toUnsignedString(System.nanoTime());
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("8").load().migrate();
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("SET search_path TO " + schema);
                sql.execute("""
                    INSERT INTO users(id, username) VALUES (100, 'legacy_known'), (200, 'legacy_unknown'), (300, 'legacy_empty');
                    INSERT INTO categories(id, user_id, name, is_default) VALUES
                        (100, 100, '📺 Séries & Animes', true),
                        (101, 100, '🎬 Favoritos', false),
                        (102, 100, '📦 Filmes e livros', false),
                        (200, 200, '📺 Séries & Animes', true),
                        (300, 300, '📺 Séries & Animes', true);
                    SELECT setval('categories_id_seq', 300);
                    INSERT INTO subcategories(id, category_id, name) VALUES (100, 100, 'Favoritas'), (200, 200, 'Favoritas');
                    SELECT setval('subcategories_id_seq', 200);
                    INSERT INTO works(id, category_id, subcategory_id, user_id, title, image_url, score, final_score) VALUES
                        (100, 100, 100, 100, 'Série conhecida', 'https://image.tmdb.org/t/p/w500/tv.jpg', 9, 9),
                        (101, 100, 100, 100, 'Anime conhecido', 'https://cdn.myanimelist.net/images/anime/a.jpg', 8, 8),
                        (200, 200, 200, 200, 'Item sem origem', null, 7, 7),
                        (201, 200, 200, 200, 'Anime identificado', 'https://cdn.myanimelist.net/images/anime/b.jpg', 10, 10),
                        (202, 200, 200, 200, 'Série identificada', 'https://image.tmdb.org/t/p/w500/series.jpg', 8, 8);
                    INSERT INTO master_table_groups(id, user_id, name, manual_order) VALUES
                        (100, 100, 'Unificado', '["100:101", "100:100"]'),
                        (200, 200, 'Unificado', '["200:200", "200:201", "200:202"]');
                    INSERT INTO master_table_categories(master_table_id, category_id) VALUES (100, 100), (200, 200);
                    INSERT INTO takes(id, user_id, work_id, text) VALUES (100, 100, 101, 'Continua aqui');
                    INSERT INTO feed_events(user_id, type, work_id, take_id) VALUES (100, 'TAKE', 101, 100);
                    """);
            }
            var result = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertEquals(1, result.migrationsExecuted);
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("SET search_path TO " + schema);
                assertEquals("5", value(sql, "SELECT count(*) FROM works"));
                assertEquals("TV", value(sql, "SELECT template FROM categories WHERE id = 100"));
                assertEquals("MOVIE", value(sql, "SELECT template FROM categories WHERE id = 101"));
                assertEquals("CUSTOM", value(sql, "SELECT template FROM categories WHERE id = 102"));
                assertEquals("ANIME", value(sql, "SELECT template FROM works WHERE id = 101"));
                assertEquals("100", value(sql, "SELECT category_id FROM works WHERE id = 100"));
                assertEquals("8.00", value(sql, "SELECT score FROM works WHERE id = 101"));
                assertEquals("101", value(sql, "SELECT work_id FROM takes WHERE id = 100"));
                assertEquals("101", value(sql, "SELECT work_id FROM feed_events WHERE take_id = 100"));
                assertEquals("true", value(sql, "SELECT (w.category_id = s.category_id)::text FROM works w JOIN subcategories s ON s.id=w.subcategory_id WHERE w.id=101"));
                String animeCategory = value(sql, "SELECT category_id FROM works WHERE id = 101");
                assertEquals(animeCategory + ":101", value(sql, "SELECT manual_order->>0 FROM master_table_groups WHERE id=100"));
                assertEquals("2", value(sql, "SELECT count(*) FROM master_table_categories WHERE master_table_id=100"));

                assertEquals("CUSTOM", value(sql, "SELECT template FROM categories WHERE id = 200"));
                assertEquals("200", value(sql, "SELECT category_id FROM works WHERE id = 200"));
                assertEquals("true", value(sql, "SELECT details->>'legacyClassificationRequired' FROM works WHERE id = 200"));
                assertEquals("3", value(sql, "SELECT count(*) FROM master_table_categories WHERE master_table_id=200"));
                assertEquals("3", value(sql, "SELECT jsonb_array_length(manual_order) FROM master_table_groups WHERE id=200"));
                assertEquals("TV", value(sql, "SELECT template FROM works WHERE id=202"));
                assertNotEquals("200", value(sql, "SELECT category_id FROM works WHERE id=202"));
                assertEquals("true", value(sql, "SELECT (w.category_id = s.category_id)::text FROM works w JOIN subcategories s ON s.id=w.subcategory_id WHERE w.id=202"));
                assertEquals("2", value(sql, "SELECT count(*) FROM categories WHERE user_id=300"));

                assertThrows(SQLException.class, () -> sql.execute("UPDATE works SET details='[]' WHERE id=100"));
                assertThrows(SQLException.class, () -> sql.execute("UPDATE categories SET template='INVALID' WHERE id=100"));
            }
        } finally {
            // Único schema removido é o schema de teste gerado nesta execução.
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private String value(Statement sql, String query) throws SQLException {
        try (var result = sql.executeQuery(query)) {
            assertTrue(result.next());
            String value = result.getString(1);
            assertFalse(result.next());
            return value;
        }
    }
}
