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

/** Confere a V11 isolada sobre contas criadas antes do tutorial. */
@SpringBootTest
class OnboardingMigrationTest {

    @Autowired DataSource dataSource;

    @Test
    void v11_marcaLegadoDonePreservaPerfis_eMudaDefaultParaPrivado() throws Exception {
        String schema = "onboarding_migration_" + Long.toUnsignedString(System.nanoTime());
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("10").load().migrate();
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                sql.execute("INSERT INTO users(id,username) VALUES (100,'perfil_publico_legado')");
                sql.execute("INSERT INTO users(id,username,is_public) VALUES (101,'perfil_privado_legado',false)");
            }
            var result = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("11").load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            try (Connection connection = dataSource.getConnection(); AutoCloseable scope = inSchema(connection, schema);
                 Statement sql = connection.createStatement()) {
                assertThat(value(sql, "SELECT onboarding_step FROM users WHERE id=100")).isEqualTo("DONE");
                assertThat(value(sql, "SELECT onboarding_step FROM users WHERE id=101")).isEqualTo("DONE");
                assertThat(value(sql, "SELECT is_public FROM users WHERE id=100")).isEqualTo("t");
                assertThat(value(sql, "SELECT is_public FROM users WHERE id=101")).isEqualTo("f");
                sql.execute("INSERT INTO users(id,username) VALUES (102,'nova_conta_sql')");
                assertThat(value(sql, "SELECT is_public FROM users WHERE id=102")).isEqualTo("f");
                assertThat(value(sql, "SELECT onboarding_step FROM users WHERE id=102")).isEqualTo("DONE");
                assertThatThrownBy(() -> sql.execute("UPDATE users SET onboarding_step=NULL WHERE id=102"))
                        .isInstanceOf(SQLException.class);
                // A lista de etapas é validada pelo enum no código, sem CHECK no banco.
                sql.execute("UPDATE users SET onboarding_step='FUTURE' WHERE id=102");
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
        return () -> connection.setSchema(original);
    }
}
