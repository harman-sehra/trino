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
import io.trino.plugin.hive.HiveCompressionCodec;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.trino.plugin.hive.HiveCompressionCodec.GZIP;
import static io.trino.plugin.iceberg.IcebergCatalogType.HADOOP;
import static io.trino.plugin.iceberg.IcebergCatalogType.HIVE;
import static io.trino.plugin.iceberg.IcebergFileFormat.ORC;
import static io.trino.plugin.iceberg.IcebergFileFormat.PARQUET;

public class TestIcebergConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(IcebergConfig.class)
                .setCatalogType(HIVE)
                .setFileFormat(ORC)
                .setCompressionCodec(GZIP)
                .setUseFileSizeFromMetadata(true)
                .setMaxPartitionsPerWriter(100));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("iceberg.catalog-type", "hadoop")
                .put("iceberg.file-format", "Parquet")
                .put("iceberg.compression-codec", "NONE")
                .put("iceberg.use-file-size-from-metadata", "false")
                .put("iceberg.max-partitions-per-writer", "222")
                .build();

        IcebergConfig expected = new IcebergConfig()
                .setCatalogType(HADOOP)
                .setFileFormat(PARQUET)
                .setCompressionCodec(HiveCompressionCodec.NONE)
                .setUseFileSizeFromMetadata(false)
                .setMaxPartitionsPerWriter(222);

        assertFullMapping(properties, expected);
    }
}
