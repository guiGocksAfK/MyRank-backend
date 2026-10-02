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

/**
 * Aplica V1–V8 num schema descartável, insere dados no formato antigo e roda a V9.
 * Precisa de um Postgres (o mesmo dos testes de contexto).
 */
@SpringBootTest
class TableTemplateMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void v9ClassificaAsTabelasAntigas_eTransformaSeriesEAnimesEmTabelaMista() throws Exception {
        String schema = "template_migration_test_" + Long.toUnsignedString(System.nanoTime());
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("8").load().migrate();
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                sql.execute("""
                    INSERT INTO users(id, username) VALUES (100, 'legado');
                    INSERT INTO categories(id, user_id, name, is_default) VALUES
                        (100, 100, '📺 Séries & Animes', true),
                        (101, 100, '🎬 Favoritos', false),
                        (102, 100, 'Livros que reli', false),
                        (103, 100, '📦 Restaurantes', false);
                    INSERT INTO subcategories(id, category_id, name) VALUES (100, 100, 'Favoritas');
                    INSERT INTO works(id, category_id, subcategory_id, user_id, title, image_url, score, final_score) VALUES
                        (100, 100, 100, 100, 'Série', 'https://image.tmdb.org/t/p/w500/tv.jpg', 9, 9),
                        (101, 100, 100, 100, 'Anime', 'https://cdn.myanimelist.net/images/anime/a.jpg', 8, 8),
                        (102, 100, null, 100, 'Sem capa', null, 7, 7),
                        (103, 101, null, 100, 'Filme', null, 6, 6),
                        (104, 103, null, 100, 'Pizzaria', 'https://cdn.myanimelist.net/x.jpg', 5, 5);
                    INSERT INTO master_table_groups(id, user_id, name, manual_order) VALUES
                        (100, 100, 'Unificado', '["100:101", "100:100"]');
                    INSERT INTO master_table_categories(master_table_id, category_id) VALUES (100, 100);
                    INSERT INTO takes(id, user_id, work_id, text) VALUES (100, 100, 101, 'Continua aqui');
                    """);
            }
            var result = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("9").load().migrate();
            assertEquals(1, result.migrationsExecuted);
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                // A tabela mista continua uma só, agora com dois tipos na ordem Séries, Animes.
                assertEquals("TV,ANIME", value(sql, templatesOf(100)));
                assertEquals("MOVIE", value(sql, templatesOf(101)));
                assertEquals("BOOK", value(sql, templatesOf(102)));
                assertEquals("CUSTOM", value(sql, templatesOf(103)));
                assertEquals("4", value(sql, "SELECT count(*) FROM categories"));

                // Itens: capa do MyAnimeList vira anime só onde a tabela tem Animes.
                assertEquals("TV", value(sql, "SELECT template FROM works WHERE id = 100"));
                assertEquals("ANIME", value(sql, "SELECT template FROM works WHERE id = 101"));
                assertEquals("TV", value(sql, "SELECT template FROM works WHERE id = 102"));
                assertEquals("MOVIE", value(sql, "SELECT template FROM works WHERE id = 103"));
                assertEquals("CUSTOM", value(sql, "SELECT template FROM works WHERE id = 104"));

                // Nada saiu do lugar.
                assertEquals("100", value(sql, "SELECT category_id FROM works WHERE id = 101"));
                assertEquals("100", value(sql, "SELECT subcategory_id FROM works WHERE id = 101"));
                assertEquals("[\"100:101\", \"100:100\"]", value(sql, "SELECT manual_order::text FROM master_table_groups WHERE id = 100"));
                assertEquals("101", value(sql, "SELECT work_id FROM takes WHERE id = 100"));
                assertEquals("{}", value(sql, "SELECT details::text FROM works WHERE id = 100"));

                assertThrows(SQLException.class, () -> sql.execute("UPDATE works SET details = '[]' WHERE id = 100"));
            }
        } finally {
            // Só o schema descartável criado aqui é removido.
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private static String templatesOf(long categoryId) {
        return "SELECT string_agg(template, ',' ORDER BY position) FROM category_templates WHERE category_id = " + categoryId;
    }

    private AutoCloseable inSchema(Connection connection, String schema) throws SQLException {
        String original = connection.getSchema();
        connection.setSchema(schema);
        // Evita contaminar os próximos testes com o schema descartável desta migration.
        return () -> connection.setSchema(original);
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
