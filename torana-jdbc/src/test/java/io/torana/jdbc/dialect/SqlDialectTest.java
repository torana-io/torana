package io.torana.jdbc.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlDialectTest {

    @Test
    void h2DialectGeneratesCorrectInsertSql() {
        H2Dialect dialect = new H2Dialect();
        String sql = dialect.getInsertSql("audit_entries");

        assertThat(sql).contains("INSERT INTO audit_entries");
        assertThat(sql).contains("id, action, occurred_at, outcome");
        assertThat(sql).contains("VALUES");
    }

    @Test
    void h2DialectGeneratesCorrectSelectByIdSql() {
        H2Dialect dialect = new H2Dialect();
        String sql = dialect.getSelectByIdSql("audit_entries");

        assertThat(sql).isEqualTo("SELECT * FROM audit_entries WHERE id = ?");
    }

    @Test
    void h2DialectGeneratesCorrectQueryBaseSql() {
        H2Dialect dialect = new H2Dialect();
        String sql = dialect.getQueryBaseSql("audit_entries");

        assertThat(sql).isEqualTo("SELECT * FROM audit_entries WHERE 1=1");
    }

    @Test
    void h2DialectGeneratesCorrectCountSql() {
        H2Dialect dialect = new H2Dialect();
        String sql = dialect.getCountSql("audit_entries");

        assertThat(sql).isEqualTo("SELECT COUNT(*) FROM audit_entries WHERE 1=1");
    }

    @Test
    void h2DialectReturnsJsonAsString() {
        H2Dialect dialect = new H2Dialect();
        String json = "{\"key\":\"value\"}";

        Object result = dialect.toJsonValue(json);

        assertThat(result).isEqualTo(json);
    }

    @Test
    void h2DialectReturnsCorrectName() {
        H2Dialect dialect = new H2Dialect();
        assertThat(dialect.name()).isEqualTo("h2");
    }

    @Test
    void postgreSqlDialectGeneratesCorrectInsertSql() {
        PostgreSqlDialect dialect = new PostgreSqlDialect();
        String sql = dialect.getInsertSql("audit_entries");

        assertThat(sql).contains("INSERT INTO audit_entries");
        assertThat(sql).contains("::jsonb");
    }

    @Test
    void postgreSqlDialectReturnsCorrectName() {
        PostgreSqlDialect dialect = new PostgreSqlDialect();
        assertThat(dialect.name()).isEqualTo("postgresql");
    }

    @Test
    void mySqlDialectGeneratesCorrectInsertSql() {
        MySqlDialect dialect = new MySqlDialect();
        String sql = dialect.getInsertSql("audit_entries");

        assertThat(sql).contains("INSERT INTO audit_entries");
        assertThat(sql).doesNotContain("::jsonb");
    }

    @Test
    void mySqlDialectReturnsCorrectName() {
        MySqlDialect dialect = new MySqlDialect();
        assertThat(dialect.name()).isEqualTo("mysql");
    }

    @Test
    void dialectDetectorReturnsH2ForH2Database() {
        SqlDialect dialect = DialectDetector.forDatabase("H2");
        assertThat(dialect).isInstanceOf(H2Dialect.class);
    }

    @Test
    void dialectDetectorReturnsPostgreSqlForPostgresDatabase() {
        SqlDialect dialect = DialectDetector.forDatabase("PostgreSQL");
        assertThat(dialect).isInstanceOf(PostgreSqlDialect.class);
    }

    @Test
    void dialectDetectorReturnsPostgreSqlForPostgresVariant() {
        SqlDialect dialect = DialectDetector.forDatabase("postgres");
        assertThat(dialect).isInstanceOf(PostgreSqlDialect.class);
    }

    @Test
    void dialectDetectorReturnsMySqlForMySqlDatabase() {
        SqlDialect dialect = DialectDetector.forDatabase("MySQL");
        assertThat(dialect).isInstanceOf(MySqlDialect.class);
    }

    @Test
    void dialectDetectorReturnsMySqlForMariaDbDatabase() {
        SqlDialect dialect = DialectDetector.forDatabase("MariaDB");
        assertThat(dialect).isInstanceOf(MySqlDialect.class);
    }

    @Test
    void dialectDetectorReturnsH2ForUnknownDatabase() {
        SqlDialect dialect = DialectDetector.forDatabase("UnknownDB");
        assertThat(dialect).isInstanceOf(H2Dialect.class);
    }
}
