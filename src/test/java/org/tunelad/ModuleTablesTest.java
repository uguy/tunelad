package org.tunelad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modules own the tables that carry their prefix, and refer to each other by id or key only: no foreign key may
 * join tables of two modules.
 */
@SpringBootTest
@Import(TestContainerDefinitions.class)
class ModuleTablesTest {

    @Autowired
    DatabaseClient db;

    @Test
    void no_foreign_key_crosses_a_module_prefix() {
        var foreignKeys = db.sql("""
                        SELECT tc.table_name AS child, ccu.table_name AS parent
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.constraint_schema
                        WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = current_schema()""")
                .map(row -> new String[]{row.get("child", String.class), row.get("parent", String.class)})
                .all().collectList().block();

        assertThat(foreignKeys).isNotEmpty();
        assertThat(foreignKeys).allSatisfy(fk -> assertThat(prefix(fk[0])).isEqualTo(prefix(fk[1])));
    }

    private static String prefix(String table) {
        return table.substring(0, table.indexOf('_'));
    }
}
