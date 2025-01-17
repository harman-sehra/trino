/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.iceberg;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.trino.Session;
import io.trino.plugin.hive.HdfsConfig;
import io.trino.plugin.hive.HdfsConfiguration;
import io.trino.plugin.hive.HdfsConfigurationInitializer;
import io.trino.plugin.hive.HdfsEnvironment;
import io.trino.plugin.hive.HiveHdfsConfiguration;
import io.trino.plugin.hive.NodeVersion;
import io.trino.plugin.hive.authentication.NoHdfsAuthentication;
import io.trino.plugin.hive.metastore.HiveMetastore;
import io.trino.plugin.hive.metastore.MetastoreConfig;
import io.trino.plugin.hive.metastore.file.FileHiveMetastore;
import io.trino.plugin.hive.metastore.file.FileHiveMetastoreConfig;
import io.trino.plugin.hive.testing.TestingHivePlugin;
import io.trino.spi.security.Identity;
import io.trino.spi.security.SelectedRole;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.DistributedQueryRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Optional;

import static io.trino.spi.security.SelectedRole.Type.ROLE;
import static io.trino.testing.TestingSession.testSessionBuilder;
import static org.testng.Assert.assertEquals;

public class TestIcebergMetadataListing
        extends AbstractTestQueryFramework
{
    private HiveMetastore metastore;

    @Override
    protected DistributedQueryRunner createQueryRunner()
            throws Exception
    {
        Session session = testSessionBuilder()
                .setIdentity(Identity.forUser("hive")
                        .withRole("hive", new SelectedRole(ROLE, Optional.of("admin")))
                        .build())
                .build();
        DistributedQueryRunner queryRunner = DistributedQueryRunner.builder(session).build();

        File baseDir = queryRunner.getCoordinator().getBaseDataDir().resolve("iceberg_data").toFile();

        HdfsConfig hdfsConfig = new HdfsConfig();
        HdfsConfiguration hdfsConfiguration = new HiveHdfsConfiguration(new HdfsConfigurationInitializer(hdfsConfig), ImmutableSet.of());
        HdfsEnvironment hdfsEnvironment = new HdfsEnvironment(hdfsConfiguration, hdfsConfig, new NoHdfsAuthentication());

        metastore = new FileHiveMetastore(
                new NodeVersion("test_version"),
                hdfsEnvironment,
                new MetastoreConfig(),
                new FileHiveMetastoreConfig()
                        .setCatalogDirectory(baseDir.toURI().toString())
                        .setMetastoreUser("test"));

        queryRunner.installPlugin(new TestingIcebergPlugin(metastore));
        queryRunner.createCatalog("iceberg", "iceberg");
        queryRunner.installPlugin(new TestingHivePlugin(metastore));
        queryRunner.createCatalog("hive", "hive", ImmutableMap.of("hive.security", "sql-standard"));

        return queryRunner;
    }

    @BeforeClass
    public void setUp()
    {
        assertQuerySucceeds("CREATE SCHEMA hive.test_schema");
        assertQuerySucceeds("CREATE TABLE iceberg.test_schema.iceberg_table1 (_string VARCHAR, _integer INTEGER)");
        assertQuerySucceeds("CREATE TABLE iceberg.test_schema.iceberg_table2 (_double DOUBLE) WITH (partitioning = ARRAY['_double'])");
        assertQuerySucceeds("CREATE TABLE hive.test_schema.hive_table (_double DOUBLE)");
        assertEquals(ImmutableSet.copyOf(metastore.getAllTables("test_schema")), ImmutableSet.of("iceberg_table1", "iceberg_table2", "hive_table"));
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        assertQuerySucceeds("DROP TABLE IF EXISTS hive.test_schema.hive_table");
        assertQuerySucceeds("DROP TABLE IF EXISTS iceberg.test_schema.iceberg_table2");
        assertQuerySucceeds("DROP TABLE IF EXISTS iceberg.test_schema.iceberg_table1");
        assertQuerySucceeds("DROP SCHEMA IF EXISTS hive.test_schema");
    }

    @Test
    public void testTableListing()
    {
        assertQuery("SHOW TABLES FROM iceberg.test_schema", "VALUES 'iceberg_table1', 'iceberg_table2'");
    }

    @Test
    public void testTableColumnListing()
    {
        // Verify information_schema.columns does not include columns from non-Iceberg tables
        assertQuery("SELECT table_name, column_name FROM iceberg.information_schema.columns WHERE table_schema = 'test_schema'",
                "VALUES ('iceberg_table1', '_string'), ('iceberg_table1', '_integer'), ('iceberg_table2', '_double')");
    }

    @Test
    public void testTableDescribing()
    {
        assertQuery("DESCRIBE iceberg.test_schema.iceberg_table1", "VALUES ('_string', 'varchar', '', ''), ('_integer', 'integer', '', '')");
    }

    @Test
    public void testTableValidation()
    {
        assertQuerySucceeds("SELECT * FROM iceberg.test_schema.iceberg_table1");
        assertQueryFails("SELECT * FROM iceberg.test_schema.hive_table", "line 1:15: Table 'iceberg.test_schema.hive_table' does not exist");
    }
}
