package br.com.myrank;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/** Aplica somente a V10 sobre um schema na V9 e confere o contrato da coluna JSONB. */
@SpringBootTest
class CustomFieldsMigrationTest {

    @Autowired DataSource dataSource;

    @Test
    void v10InicializaLegado_eExigeArraySemRestringirTiposNoBanco() throws Exception {
        String schema = "custom_fields_migration_" + Long.toUnsignedString(System.nanoTime());
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("9").load().migrate();
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                sql.execute("INSERT INTO users(id, username) VALUES (100, 'campos_legados')");
                sql.execute("INSERT INTO categories(id, user_id, name, is_default) VALUES (100, 100, 'Legado', false)");
            }
            var result = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("10").load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                assertThat(value(sql, "SELECT custom_fields::text FROM categories WHERE id = 100")).isEqualTo("[]");
                sql.execute("INSERT INTO categories(id, user_id, name, is_default) VALUES (101, 100, 'Nova', false)");
                assertThat(value(sql, "SELECT custom_fields::text FROM categories WHERE id = 101")).isEqualTo("[]");
                assertThatThrownBy(() -> sql.execute("UPDATE categories SET custom_fields = '{}' WHERE id = 100"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.execute("UPDATE categories SET custom_fields = NULL WHERE id = 100"))
                        .isInstanceOf(SQLException.class);
                // O banco só exige um array; os tipos são responsabilidade do código.
                sql.execute("UPDATE categories SET custom_fields = '[{\"id\":\"f_future00\",\"name\":\"Futuro\",\"type\":\"FUTURE\"}]' WHERE id = 100");
                assertThat(value(sql, "SELECT custom_fields->0->>'type' FROM categories WHERE id = 100")).isEqualTo("FUTURE");
            }
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private String value(Statement sql, String query) throws SQLException {
        try (var result = sql.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private AutoCloseable inSchema(Connection connection, String schema) throws SQLException {
        String original = connection.getSchema();
        connection.setSchema(schema);
        // A conexão volta ao pool apontando para o schema original, mesmo se o teste falhar.
        return () -> connection.setSchema(original);
    }
}
